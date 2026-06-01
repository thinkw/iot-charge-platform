package com.iot.common.enums;

/**
 * 支付状态枚举
 * <p>
 * 定义充电订单的支付状态：
 * - UNPAID：未支付，用户尚未完成付款
 * - PAID：已支付，用户已完成付款
 * - REFUNDED：已退款，已退款给用户
 * </p>
 *
 * @author IoT Team
 */
public enum PayStatusEnum {

    /**
     * 未支付
     */
    UNPAID(0, "未支付"),

    /**
     * 已支付
     */
    PAID(1, "已支付"),

    /**
     * 已退款
     */
    REFUNDED(2, "已退款");

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
    PayStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据状态码获取对应的枚举
     *
     * @param code 状态码
     * @return 对应的支付状态枚举
     * @throws IllegalArgumentException 当状态码无效时抛出
     */
    public static PayStatusEnum fromCode(int code) {
        for (PayStatusEnum status : PayStatusEnum.values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的支付状态码: " + code);
    }

    // ==================== Getters ====================

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
