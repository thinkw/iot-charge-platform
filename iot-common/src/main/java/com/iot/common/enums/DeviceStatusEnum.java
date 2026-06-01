package com.iot.common.enums;

/**
 * 设备状态枚举
 * <p>
 * 定义充电设备的所有可能运行状态：
 * - OFFLINE：设备离线，无法通信
 * - IDLE：设备空闲，等待用户使用
 * - CHARGING：设备充电中
 * - FAULT：设备故障
 * - LOCKED：设备锁定（如被预约或维护）
 * </p>
 *
 * @author IoT Team
 */
public enum DeviceStatusEnum {

    /**
     * 离线
     */
    OFFLINE(0, "离线"),

    /**
     * 空闲
     */
    IDLE(1, "空闲"),

    /**
     * 充电中
     */
    CHARGING(2, "充电中"),

    /**
     * 故障
     */
    FAULT(3, "故障"),

    /**
     * 锁定
     */
    LOCKED(4, "锁定");

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
    DeviceStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据状态码获取对应的枚举
     *
     * @param code 状态码
     * @return 对应的设备状态枚举
     * @throws IllegalArgumentException 当状态码无效时抛出
     */
    public static DeviceStatusEnum fromCode(int code) {
        for (DeviceStatusEnum status : DeviceStatusEnum.values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的设备状态码: " + code);
    }

    // ==================== Getters ====================

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
