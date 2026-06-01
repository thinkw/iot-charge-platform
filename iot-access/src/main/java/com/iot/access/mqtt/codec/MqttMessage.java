package com.iot.access.mqtt.codec;

/**
 * MQTT 消息模型
 * <p>
 * 表示一个已解码的 MQTT 报文，包含消息类型、QoS 等级、主题、负载等字段。
 * 不同消息类型使用的字段不同，具体参见各字段注释。
 * </p>
 *
 * @author IoT Team
 */
public class MqttMessage {

    /** 消息类型 */
    private MqttMessageType messageType;

    /** QoS 等级（仅 PUBLISH 消息使用，0/1/2） */
    private int qos;

    /** 是否保留消息（仅 PUBLISH 消息使用） */
    private boolean retain;

    /** 是否重复发送（仅 PUBLISH 消息使用，DUP flag） */
    private boolean dup;

    /** 报文标识符（PUBLISH QoS>0 / PUBACK / SUBSCRIBE / SUBACK 使用） */
    private int packetId;

    /** 主题名称（PUBLISH / SUBSCRIBE 使用） */
    private String topic;

    /** 消息负载（PUBLISH 消息的内容） */
    private byte[] payload;

    /** 协议名称（CONNECT 消息使用，固定为 "MQTT"） */
    private String protocolName;

    /** 协议版本（CONNECT 消息使用，固定为 4 表示 MQTT 3.1.1） */
    private int protocolVersion;

    /** 客户端标识（CONNECT 消息使用） */
    private String clientId;

    /** 用户名（CONNECT 消息使用） */
    private String username;

    /** 密码（CONNECT 消息使用，原始字节） */
    private byte[] password;

    /** 保活时间，秒（CONNECT 消息使用） */
    private int keepAlive;

    /** 连接返回码（CONNACK 消息使用） */
    private int returnCode;

    // ==================== 工厂方法 ====================

    /**
     * 创建 CONNACK 响应消息
     *
     * @param returnCode 返回码：0-接受，1-协议版本不支持，2-标识符被拒绝，3-服务不可用，4-用户名密码错误，5-未授权
     */
    public static MqttMessage connack(int returnCode) {
        MqttMessage msg = new MqttMessage();
        msg.messageType = MqttMessageType.CONNACK;
        msg.returnCode = returnCode;
        return msg;
    }

    /**
     * 创建 PINGRESP 响应消息
     */
    public static MqttMessage pingresp() {
        MqttMessage msg = new MqttMessage();
        msg.messageType = MqttMessageType.PINGRESP;
        return msg;
    }

    /**
     * 创建 PUBACK 响应消息
     *
     * @param packetId 对应的 PUBLISH 报文标识符
     */
    public static MqttMessage puback(int packetId) {
        MqttMessage msg = new MqttMessage();
        msg.messageType = MqttMessageType.PUBACK;
        msg.packetId = packetId;
        return msg;
    }

    /**
     * 创建 SUBACK 响应消息
     *
     * @param packetId 对应的 SUBSCRIBE 报文标识符
     */
    public static MqttMessage suback(int packetId) {
        MqttMessage msg = new MqttMessage();
        msg.messageType = MqttMessageType.SUBACK;
        msg.packetId = packetId;
        return msg;
    }

    /**
     * 创建 PUBLISH 消息（用于服务端向设备下发指令）
     *
     * @param topic   目标主题
     * @param payload 消息内容
     * @param qos     QoS 等级
     */
    public static MqttMessage publish(String topic, byte[] payload, int qos) {
        MqttMessage msg = new MqttMessage();
        msg.messageType = MqttMessageType.PUBLISH;
        msg.topic = topic;
        msg.payload = payload;
        msg.qos = qos;
        return msg;
    }

    // ==================== Getters & Setters ====================

    public MqttMessageType getMessageType() { return messageType; }
    public void setMessageType(MqttMessageType messageType) { this.messageType = messageType; }

    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }

    public boolean isRetain() { return retain; }
    public void setRetain(boolean retain) { this.retain = retain; }

    public boolean isDup() { return dup; }
    public void setDup(boolean dup) { this.dup = dup; }

    public int getPacketId() { return packetId; }
    public void setPacketId(int packetId) { this.packetId = packetId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public byte[] getPayload() { return payload; }
    public void setPayload(byte[] payload) { this.payload = payload; }

    public String getPayloadAsString() {
        return payload != null ? new String(payload, java.nio.charset.StandardCharsets.UTF_8) : null;
    }

    public String getProtocolName() { return protocolName; }
    public void setProtocolName(String protocolName) { this.protocolName = protocolName; }

    public int getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(int protocolVersion) { this.protocolVersion = protocolVersion; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public byte[] getPassword() { return password; }
    public void setPassword(byte[] password) { this.password = password; }

    public String getPasswordAsString() {
        return password != null ? new String(password, java.nio.charset.StandardCharsets.UTF_8) : null;
    }

    public int getKeepAlive() { return keepAlive; }
    public void setKeepAlive(int keepAlive) { this.keepAlive = keepAlive; }

    public int getReturnCode() { return returnCode; }
    public void setReturnCode(int returnCode) { this.returnCode = returnCode; }
}
