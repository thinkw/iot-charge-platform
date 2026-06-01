package com.iot.common.enums;

/**
 * 告警级别枚举
 * <p>
 * 定义系统告警的严重程度级别：
 * - GENERAL：一般告警，不影响系统正常运行，仅需记录关注
 * - IMPORTANT：重要告警，可能影响部分功能，需要及时处理
 * - URGENT：紧急告警，严重影响系统运行，需要立即处理
 * </p>
 *
 * @author IoT Team
 */
public enum AlarmLevelEnum {

    /**
     * 一般
     */
    GENERAL(1, "一般"),

    /**
     * 重要
     */
    IMPORTANT(2, "重要"),

    /**
     * 紧急
     */
    URGENT(3, "紧急");

    /** 级别码 */
    private final int code;

    /** 级别描述 */
    private final String desc;

    /**
     * 构造方法
     *
     * @param code 级别码
     * @param desc 级别描述
     */
    AlarmLevelEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据级别码获取对应的枚举
     *
     * @param code 级别码
     * @return 对应的告警级别枚举
     * @throws IllegalArgumentException 当级别码无效时抛出
     */
    public static AlarmLevelEnum fromCode(int code) {
        for (AlarmLevelEnum level : AlarmLevelEnum.values()) {
            if (level.code == code) {
                return level;
            }
        }
        throw new IllegalArgumentException("无效的告警级别码: " + code);
    }

    // ==================== Getters ====================

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
