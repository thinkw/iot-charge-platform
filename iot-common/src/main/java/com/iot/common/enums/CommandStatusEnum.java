package com.iot.common.enums;

/**
 * 指令生命周期状态枚举
 * <p>
 * 定义一条远程指令从下发到终态的完整生命周期状态。
 * 指令通过 MQTT 下发后，经过 PENDING → SENT → ACKED → SUCCESS/DEVICE_ERROR/TIMEOUT 的状态流转。
 * </p>
 *
 * <pre>
 * PENDING ──(发送)──> SENT ──(PUBACK)──> ACKED ──(设备响应)──> SUCCESS / DEVICE_ERROR
 *    ↑                                                           │
 *    └──────────────────(超时+超过最大重试)─────────────────────────┘
 *                                               TIMEOUT
 * </pre>
 *
 * @author IoT Team
 */
public enum CommandStatusEnum {

    /**
     * 待发送：指令已生成但尚未通过 MQTT 下发
     */
    PENDING(0, "待发送"),

    /**
     * 已发送：指令已通过 MQTT PUBLISH 下发，等待设备 PUBACK
     */
    SENT(1, "已发送"),

    /**
     * 已送达：已收到设备 PUBACK，指令已到达设备但尚未执行确认
     */
    ACKED(2, "已送达"),

    /**
     * 执行成功：设备回复了 SUCCESS 响应，指令已成功执行
     */
    SUCCESS(3, "执行成功"),

    /**
     * 设备错误：设备回复了 ERROR 响应，指令执行失败
     */
    DEVICE_ERROR(4, "设备错误"),

    /**
     * 超时：指令在总超时时间内未收到设备响应，且重试次数已耗尽
     */
    TIMEOUT(5, "超时");

    /** 状态码 */
    private final int code;

    /** 状态描述 */
    private final String desc;

    /**
     * 构造方法
     *
     * @param code 状态码
     * @param desc 状态描述
     */
    CommandStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据状态码获取对应的枚举
     *
     * @param code 状态码
     * @return 对应的指令状态枚举
     * @throws IllegalArgumentException 当状态码无效时抛出
     */
    public static CommandStatusEnum fromCode(int code) {
        for (CommandStatusEnum status : CommandStatusEnum.values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的指令状态码: " + code);
    }

    /**
     * 判断是否为终态（不可再变更）
     *
     * @return true 如果状态为终态（SUCCESS/DEVICE_ERROR/TIMEOUT）
     */
    public boolean isFinal() {
        return this == SUCCESS || this == DEVICE_ERROR || this == TIMEOUT;
    }

    // ==================== Getters ====================

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
