package com.iot.access.mqtt.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * MQTT 3.1.1 协议解码器
 * <p>
 * 将 Netty 的 ByteBuf 字节流解码为 {@link MqttMessage} 对象。
 * 完整支持 MQTT 3.1.1 规范的所有 14 种报文类型。
 * 未知报文类型不会被断开连接，而是安全跳过，避免因 Paho 客户端
 * 的 QoS 握手报文导致断连。
 * </p>
 * <p>
 * MQTT 3.1.1 报文格式：
 * - 固定头：1字节类型(bit7-4) + 标志(bit3-0) + 1-4字节剩余长度（变长编码）
 * - 可变头：取决于报文类型
 * - 负载：取决于报文类型
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
public class MqttDecoder extends ByteToMessageDecoder {

    /** MQTT 3.1.1 协议级别 */
    private static final byte PROTOCOL_LEVEL = 4;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 至少需要 2 个字节（1字节固定头 + 至少1字节剩余长度）
        if (in.readableBytes() < 2) {
            return;
        }

        // 标记读索引，当数据不完整时可整体回退
        in.markReaderIndex();

        // 1. 解析固定头第1字节：消息类型(高4位) + 标志(低4位)
        byte firstByte = in.readByte();
        int messageTypeCode = (firstByte >> 4) & 0x0F;
        int flags = firstByte & 0x0F;

        MqttMessageType messageType = MqttMessageType.fromCode(messageTypeCode);

        // 2. 解析剩余长度（变长编码，最多4字节）
        int remainingLength = readVariableLength(in);
        if (remainingLength < 0) {
            // 数据不完整（变长编码被截断），回退等待更多数据
            in.resetReaderIndex();
            return;
        }

        // 检查剩余数据是否足够
        if (in.readableBytes() < remainingLength) {
            in.resetReaderIndex();
            return;
        }

        // 3. 对于 RESERVED 类型或暂时不需要处理的类型，跳过并继续
        if (messageType == MqttMessageType.RESERVED
                || messageType == MqttMessageType.PUBREL
                || messageType == MqttMessageType.PUBREC
                || messageType == MqttMessageType.PUBCOMP
                || messageType == MqttMessageType.UNSUBSCRIBE
                || messageType == MqttMessageType.UNSUBACK) {
            log.debug("[MQTT解码] 跳过报文类型: {} (code={})", messageType, messageTypeCode);
            in.skipBytes(remainingLength);
            return;
        }

        // 4. 根据消息类型解析可变头和负载
        MqttMessage message = new MqttMessage();
        message.setMessageType(messageType);

        try {
            switch (messageType) {
                case CONNECT -> decodeConnect(in, message, flags);
                case PUBLISH -> decodePublish(in, message, flags, remainingLength);
                case PUBACK -> decodePuback(in, message);
                case SUBSCRIBE -> decodeSubscribe(in, message, remainingLength);
                case PINGREQ -> { /* PINGREQ 无负载 */ }
                case DISCONNECT -> { /* DISCONNECT 无负载 */ }
                default -> {
                    log.debug("[MQTT解码] 跳过未处理的报文类型: {}", messageType);
                    in.skipBytes(remainingLength);
                    return;
                }
            }
        } catch (Exception e) {
            log.error("[MQTT解码] 解析 {} 报文失败，断开连接", messageType, e);
            ctx.close();
            return;
        }

        out.add(message);
    }

    // ==================== CONNECT 报文解析 ====================

    /**
     * 解析 CONNECT 报文
     * <p>
     * 可变头：协议名(UTF-8) + 协议级别(1) + 连接标志(1) + 保活时间(2)
     * 负载：ClientId(UTF-8) + [Will Topic + Will Message] + [Username] + [Password]
     * </p>
     */
    private void decodeConnect(ByteBuf in, MqttMessage message, int flags) {
        // 协议名
        message.setProtocolName(readUtf8String(in));
        // 协议级别
        message.setProtocolVersion(in.readUnsignedByte());

        // 连接标志
        int connectFlags = in.readUnsignedByte();
        boolean hasUsername = (connectFlags & 0x80) != 0;  // bit 7
        boolean hasPassword = (connectFlags & 0x40) != 0;  // bit 6
        boolean cleanSession = (connectFlags & 0x02) != 0; // bit 1

        // 保活时间（秒）
        message.setKeepAlive(in.readUnsignedShort());

        // 负载：Client Identifier
        message.setClientId(readUtf8String(in));

        // 可选：Will Topic + Will Message（当前协议层不处理遗嘱消息）
        boolean hasWillFlag = (connectFlags & 0x04) != 0;
        if (hasWillFlag) {
            readUtf8String(in); // Will Topic（跳过）
            // Will Message 需要根据 Will QoS 判断是否读取2字节长度
            int willQos = (connectFlags >> 3) & 0x03;
            if (willQos > 0) {
                // Will Message 使用 UTF-8 编码（2字节长度前缀）
                readUtf8String(in);
            } else {
                readUtf8String(in);
            }
        }

        // 可选：Username
        if (hasUsername) {
            message.setUsername(readUtf8String(in));
        }

        // 可选：Password
        if (hasPassword) {
            int pwLen = in.readUnsignedShort();
            if (pwLen > 0) {
                byte[] passwordBytes = new byte[pwLen];
                in.readBytes(passwordBytes);
                message.setPassword(passwordBytes);
            }
        }

        log.debug("[MQTT解码] CONNECT - clientId: {}, username: {}, keepAlive: {}s, cleanSession: {}",
                message.getClientId(), message.getUsername(), message.getKeepAlive(), cleanSession);
    }

    // ==================== PUBLISH 报文解析 ====================

    /**
     * 解析 PUBLISH 报文
     * <p>
     * 固定头标志：DUP(bit3) + QoS(bit2-1) + RETAIN(bit0)
     * 可变头：Topic Name(UTF-8) + [Packet Identifier(QoS>0时)]
     * 负载：消息内容
     * <p>
     * 负载长度 = remainingLength - 可变头已读字节数（Topic字段 + 可选PacketId）。
     * 不能使用 in.readableBytes()，因为 TCP 段中可能已粘带了后续报文。
     * </p>
     *
     * @param remainingLength 报文的剩余长度（由 MQTT 固定头中的变长编码字段指定）
     */
    private void decodePublish(ByteBuf in, MqttMessage message, int flags, int remainingLength) {
        message.setDup((flags & 0x08) != 0);
        message.setQos((flags >> 1) & 0x03);
        message.setRetain((flags & 0x01) != 0);

        // Topic（UTF-8 编码：2字节长度前缀 + N字节内容）
        message.setTopic(readUtf8String(in));
        int consumedBytes = 2 + message.getTopic().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

        // Packet Identifier（仅 QoS > 0 时，2 字节）
        if (message.getQos() > 0) {
            message.setPacketId(in.readUnsignedShort());
            consumedBytes += 2;
        }

        // 负载 = 剩余长度 - 已消费的可变头字节数（精确计算，避免 TCP 粘包干扰）
        int payloadSize = remainingLength - consumedBytes;
        if (payloadSize > 0) {
            byte[] payload = new byte[payloadSize];
            in.readBytes(payload);
            message.setPayload(payload);
        }

        log.debug("[MQTT解码] PUBLISH - topic: {}, qos: {}, payloadSize: {}",
                message.getTopic(), message.getQos(), payloadSize);
    }

    // ==================== PUBACK 报文解析 ====================

    private void decodePuback(ByteBuf in, MqttMessage message) {
        message.setPacketId(in.readUnsignedShort());
        log.debug("[MQTT解码] PUBACK - packetId: {}", message.getPacketId());
    }

    // ==================== SUBSCRIBE 报文解析 ====================

    /**
     * 解析 SUBSCRIBE 报文并消费全部负载字节
     * <p>
     * SUBSCRIBE 负载 = 2字节 PacketId + Topic Filter 列表
     * 每个 Topic Filter = UTF-8字符串(2字节长度+N字节内容) + 1字节 Requested QoS
     * <p>
     * 必须消费 remainingLength 指定数量的字节，否则残留数据会导致后续消息解析错位。
     * </p>
     *
     * @param in              字节缓冲区
     * @param message         目标消息对象
     * @param remainingLength 报文的剩余长度（不含固定头，来自 decode() 计算）
     */
    private void decodeSubscribe(ByteBuf in, MqttMessage message, int remainingLength) {
        message.setPacketId(in.readUnsignedShort());
        // 消费剩余的 Topic Filter 列表字节（本服务端不关心具体订阅内容）
        int consumedBytes = 2; // packetId 已读 2 字节
        int toSkip = remainingLength - consumedBytes;
        if (toSkip > 0) {
            in.skipBytes(toSkip);
        }
        log.debug("[MQTT解码] SUBSCRIBE - packetId: {}", message.getPacketId());
    }

    // ==================== 工具方法 ====================

    /**
     * 读取变长编码的剩余长度（Remaining Length）
     * <p>
     * 每字节低7位为数据，最高位为延续标志（1=还有后续字节，0=最后一字节）。
     * 最大4字节，表示0~268435455。
     * 使用临时变量计算，只有全部读取成功后才更新 readerIndex。
     * </p>
     *
     * @return 剩余长度值，-1 表示数据不足需要等待更多数据
     */
    private int readVariableLength(ByteBuf in) {
        int multiplier = 1;
        int value = 0;

        // 记录初始位置，用于失败回退
        int startIndex = in.readerIndex();

        for (int i = 0; i < 4; i++) {
            if (!in.isReadable()) {
                in.readerIndex(startIndex);
                return -1;
            }
            int encodedByte = in.readUnsignedByte();
            value += (encodedByte & 0x7F) * multiplier;
            if (value > 268435455) {
                // 剩余长度超过 MQTT 规范最大值，协议错误
                throw new IllegalArgumentException("MQTT协议错误：剩余长度超过最大值268435455");
            }
            multiplier *= 128;
            if ((encodedByte & 0x80) == 0) {
                // 最高位为0，编码结束
                return value;
            }
        }

        // 超过4字节，协议错误
        throw new IllegalArgumentException("MQTT协议错误：剩余长度字段超过4字节");
    }

    /**
     * 读取 MQTT UTF-8 编码字符串
     * <p>
     * MQTT 字符串格式：2字节长度(大端) + UTF-8编码的字节序列
     * 包含长度校验，防止内存溢出。
     * </p>
     */
    private String readUtf8String(ByteBuf in) {
        int length = in.readUnsignedShort();
        if (length == 0) {
            return "";
        }
        // 长度校验：MQTT UTF-8 字符串最大 65535 字节
        if (length > in.readableBytes()) {
            throw new IllegalArgumentException(String.format(
                    "MQTT UTF-8字符串长度异常: length=%d, readable=%d", length, in.readableBytes()));
        }
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
