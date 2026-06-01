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
 * 支持 CONNECT、PUBLISH、PUBACK、SUBSCRIBE、PINGREQ、DISCONNECT 等报文类型的解码。
 * </p>
 * <p>
 * MQTT 3.1.1 报文格式：
 * - 固定头：1字节类型+标志 + 1-4字节剩余长度（变长编码）
 * - 可变头：取决于报文类型
 * - 负载：取决于报文类型
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
public class MqttDecoder extends ByteToMessageDecoder {

    /** MQTT 协议名称常量 */
    private static final String PROTOCOL_NAME = "MQTT";
    /** MQTT 3.1.1 协议级别 */
    private static final byte PROTOCOL_LEVEL = 4;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 标记读索引，当数据不完整时可回退
        in.markReaderIndex();

        // 至少需要 2 个字节（1字节固定头 + 至少1字节剩余长度）
        if (in.readableBytes() < 2) {
            in.resetReaderIndex();
            return;
        }

        // 1. 解析固定头第1字节：消息类型(高4位) + 标志(低4位)
        byte firstByte = in.readByte();
        int messageTypeCode = (firstByte >> 4) & 0x0F;
        int flags = firstByte & 0x0F;

        MqttMessageType messageType;
        try {
            messageType = MqttMessageType.fromCode(messageTypeCode);
        } catch (IllegalArgumentException e) {
            log.error("[MQTT解码] 未知报文类型: {}", messageTypeCode);
            ctx.close(); // 非法报文，断开连接
            return;
        }

        // 2. 解析剩余长度（变长编码，最多4字节）
        int remainingLength = readVariableLength(in);
        if (remainingLength < 0) {
            in.resetReaderIndex();
            return; // 数据不完整
        }

        // 检查剩余数据是否足够
        if (in.readableBytes() < remainingLength) {
            in.resetReaderIndex();
            return;
        }

        // 3. 根据消息类型解析可变头和负载
        MqttMessage message = new MqttMessage();
        message.setMessageType(messageType);

        try {
            switch (messageType) {
                case CONNECT -> decodeConnect(in, message, flags);
                case PUBLISH -> decodePublish(in, message, flags, remainingLength);
                case PUBACK -> decodePuback(in, message);
                case SUBSCRIBE -> decodeSubscribe(in, message);
                case PINGREQ -> { /* PINGREQ 无负载，不需要额外解析 */ }
                case DISCONNECT -> { /* DISCONNECT 无负载，不需要额外解析 */ }
                default -> {
                    // 跳过未实现的报文（已根据 remainingLength 读取了剩余数据）
                    log.debug("[MQTT解码] 跳过未处理的报文类型: {}", messageType);
                    in.skipBytes(remainingLength);
                }
            }
        } catch (Exception e) {
            log.error("[MQTT解码] 解析 {} 报文失败", messageType, e);
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
        message.setProtocolVersion(in.readByte() & 0xFF);

        // 连接标志
        int connectFlags = in.readByte() & 0xFF;
        boolean hasUsername = (connectFlags & 0x80) != 0;  // bit 7
        boolean hasPassword = (connectFlags & 0x40) != 0;  // bit 6
        boolean cleanSession = (connectFlags & 0x02) != 0; // bit 1

        // 保活时间（秒）
        message.setKeepAlive(in.readUnsignedShort());

        // 负载：Client Identifier
        message.setClientId(readUtf8String(in));

        // 可选：Username
        if (hasUsername) {
            message.setUsername(readUtf8String(in));
        }

        // 可选：Password
        if (hasPassword) {
            int pwLen = in.readUnsignedShort();
            byte[] passwordBytes = new byte[pwLen];
            in.readBytes(passwordBytes);
            message.setPassword(passwordBytes);
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
     * </p>
     */
    private void decodePublish(ByteBuf in, MqttMessage message, int flags, int remainingLength) {
        message.setDup((flags & 0x08) != 0);
        message.setQos((flags >> 1) & 0x03);
        message.setRetain((flags & 0x01) != 0);

        // Topic
        message.setTopic(readUtf8String(in));

        // Packet Identifier（仅 QoS > 0 时）
        if (message.getQos() > 0) {
            message.setPacketId(in.readUnsignedShort());
        }

        // Payload（剩余字节数 = remainingLength - 已读字节数（含已读的长度字段自身））
        int payloadSize = in.readableBytes();
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

    private void decodeSubscribe(ByteBuf in, MqttMessage message) {
        message.setPacketId(in.readUnsignedShort());
        // 跳过 Topic Filter 列表（简化处理，我们只需要 packetId）
        log.debug("[MQTT解码] SUBSCRIBE - packetId: {}", message.getPacketId());
    }

    // ==================== 工具方法 ====================

    /**
     * 读取变长编码的剩余长度（Remaining Length）
     * <p>
     * 每字节低7位为数据，最高位为延续标志（1=还有后续字节，0=最后一字节）。
     * 最大4字节，表示0~268435455。
     * </p>
     *
     * @return 剩余长度值，-1 表示数据不足需要等待更多数据
     */
    private int readVariableLength(ByteBuf in) {
        int multiplier = 1;
        int value = 0;
        int encodedByte;

        // 标记读索引以便数据不足时回退
        int startIndex = in.readerIndex();

        for (int i = 0; i < 4; i++) {
            if (!in.isReadable()) {
                // 数据不足，恢复读索引
                in.readerIndex(startIndex);
                return -1;
            }
            encodedByte = in.readByte() & 0xFF;
            value += (encodedByte & 0x7F) * multiplier;
            multiplier *= 128;
            if ((encodedByte & 0x80) == 0) {
                return value;
            }
        }

        // 超过4字节，协议错误
        throw new IllegalArgumentException("剩余长度字段超过4字节，协议错误");
    }

    /**
     * 读取 MQTT UTF-8 编码字符串
     * <p>
     * MQTT 字符串格式：2字节长度(大端) + UTF-8编码的字节序列
     * </p>
     */
    private String readUtf8String(ByteBuf in) {
        int length = in.readUnsignedShort();
        if (length == 0) {
            return "";
        }
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
