package com.iot.access.mqtt.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

/**
 * MQTT 3.1.1 协议编码器
 * <p>
 * 将 {@link MqttMessage} 对象编码为 MQTT 协议二进制字节流。
 * 支持 CONNACK、PUBLISH、PUBACK、SUBACK、PINGRESP 等报文类型的编码。
 * </p>
 *
 * @author IoT Team
 */
public class MqttEncoder extends MessageToByteEncoder<MqttMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, MqttMessage msg, ByteBuf out) {
        switch (msg.getMessageType()) {
            case CONNACK -> encodeConnack(msg, out);
            case PUBLISH -> encodePublish(msg, out);
            case PUBACK -> encodePuback(msg, out);
            case SUBACK -> encodeSuback(msg, out);
            case PINGRESP -> encodePingresp(out);
            default -> throw new IllegalArgumentException("不支持的编码报文类型: " + msg.getMessageType());
        }
    }

    // ==================== CONNACK ====================

    /**
     * CONNACK 报文格式：
     * 固定头：0x20 0x02
     * 可变头：Session Present(1) + Return Code(1)
     */
    private void encodeConnack(MqttMessage msg, ByteBuf out) {
        // 固定头
        out.writeByte(0x20);  // CONNACK type + flags
        out.writeByte(0x02);  // Remaining length = 2

        // 可变头
        out.writeByte(0x00);  // Session Present = 0 (no existing session)
        out.writeByte(msg.getReturnCode());  // Return Code
    }

    // ==================== PUBLISH ====================

    /**
     * PUBLISH 报文格式：
     * 固定头：type(4bit) + DUP(1) + QoS(2) + RETAIN(1) | Remaining Length(变长)
     * 可变头：Topic Name(UTF-8) + [PacketId(2, QoS>0)]
     * 负载：消息内容
     */
    private void encodePublish(MqttMessage msg, ByteBuf out) {
        byte firstByte = (byte) (MqttMessageType.PUBLISH.getCode() << 4);
        if (msg.isDup()) firstByte |= 0x08;
        firstByte |= (msg.getQos() & 0x03) << 1;
        if (msg.isRetain()) firstByte |= 0x01;

        // 计算剩余长度
        byte[] topicBytes = msg.getTopic().getBytes(StandardCharsets.UTF_8);
        int remainingLength = 2 + topicBytes.length;  // topic长度(2) + topic字节
        if (msg.getQos() > 0) {
            remainingLength += 2;  // packetId
        }
        if (msg.getPayload() != null) {
            remainingLength += msg.getPayload().length;
        }

        // 写入固定头
        out.writeByte(firstByte);
        writeVariableLength(out, remainingLength);

        // 写入可变头：Topic
        writeUtf8String(out, msg.getTopic());

        // 写入可变头：PacketId（QoS > 0 时）
        if (msg.getQos() > 0) {
            out.writeShort(msg.getPacketId());
        }

        // 写入负载
        if (msg.getPayload() != null && msg.getPayload().length > 0) {
            out.writeBytes(msg.getPayload());
        }
    }

    // ==================== PUBACK ====================

    /**
     * PUBACK 报文格式：
     * 固定头：0x40 0x02
     * 可变头：Packet Identifier(2)
     */
    private void encodePuback(MqttMessage msg, ByteBuf out) {
        out.writeByte(0x40);
        out.writeByte(0x02);
        out.writeShort(msg.getPacketId());
    }

    // ==================== SUBACK ====================

    /**
     * SUBACK 报文格式：
     * 固定头：0x90 0x03
     * 可变头：Packet Identifier(2) + Return Code(1)
     */
    private void encodeSuback(MqttMessage msg, ByteBuf out) {
        out.writeByte(0x90);
        out.writeByte(0x03);
        out.writeShort(msg.getPacketId());
        out.writeByte(0x00);  // 0x00 = Success - Maximum QoS 0
    }

    // ==================== PINGRESP ====================

    /**
     * PINGRESP 报文格式：
     * 固定头：0xD0 0x00（无负载）
     */
    private void encodePingresp(ByteBuf out) {
        out.writeByte(0xD0);
        out.writeByte(0x00);
    }

    // ==================== 工具方法 ====================

    /**
     * 写入变长编码的剩余长度（Remaining Length）
     */
    private void writeVariableLength(ByteBuf out, int length) {
        do {
            int digit = length % 128;
            length /= 128;
            if (length > 0) {
                digit |= 0x80;
            }
            out.writeByte(digit);
        } while (length > 0);
    }

    /**
     * 写入 MQTT UTF-8 编码字符串（2字节长度前缀 + UTF-8字节）
     */
    private void writeUtf8String(ByteBuf out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.writeBytes(bytes);
    }
}
