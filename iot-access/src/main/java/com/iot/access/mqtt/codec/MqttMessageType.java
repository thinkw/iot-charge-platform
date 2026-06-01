package com.iot.access.mqtt.codec;

/**
 * MQTT 3.1.1 消息类型枚举
 * <p>
 * 定义 MQTT 协议中所有 14 种报文类型及其对应的固定头字节值。
 * 完整支持 MQTT 3.1.1 规范的所有报文类型。
 * </p>
 *
 * @author IoT Team
 */
public enum MqttMessageType {

    /** 保留 */
    RESERVED(0),
    /** 连接请求：客户端 → 服务端 */
    CONNECT(1),
    /** 连接确认：服务端 → 客户端 */
    CONNACK(2),
    /** 发布消息：双向 */
    PUBLISH(3),
    /** 发布确认（QoS 1）：双向 */
    PUBACK(4),
    /** 发布收到（QoS 2，第一步）：双向 */
    PUBREC(5),
    /** 发布释放（QoS 2，第二步）：双向 */
    PUBREL(6),
    /** 发布完成（QoS 2，第三步）：双向 */
    PUBCOMP(7),
    /** 订阅请求：客户端 → 服务端 */
    SUBSCRIBE(8),
    /** 订阅确认：服务端 → 客户端 */
    SUBACK(9),
    /** 取消订阅：客户端 → 服务端 */
    UNSUBSCRIBE(10),
    /** 取消订阅确认：服务端 → 客户端 */
    UNSUBACK(11),
    /** 心跳请求：客户端 → 服务端 */
    PINGREQ(12),
    /** 心跳响应：服务端 → 客户端 */
    PINGRESP(13),
    /** 断开连接：客户端 → 服务端 */
    DISCONNECT(14);

    private final int code;

    MqttMessageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 根据报文类型码获取枚举
     *
     * @param code 报文类型码（取固定头第一个字节的高4位）
     * @return 对应的消息类型，未知类型返回 RESERVED(0)
     */
    public static MqttMessageType fromCode(int code) {
        for (MqttMessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return RESERVED;
    }
}
