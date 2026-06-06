package com.iot.common.enums;

/**
 * 订单状态枚举
 * <p>
 * 定义充电订单的所有可能状态：
 * - PAY_PENDING：待支付，用户创建订单后尚未完成支付
 * - CHARGING：充电中，已支付且设备正在充电
 * - COMPLETED：已完成，充电结束
 * - CANCELLED：已取消，用户在支付前取消订单
 * - ABNORMAL：异常，充电过程中出现异常情况
 * </p>
 *
 * @author IoT Team
 */
public enum OrderStatusEnum {

    /**
     * 待支付
     */
    PAY_PENDING(0, "待支付"),

    /**
     * 充电中
     */
    CHARGING(1, "充电中"),

    /**
     * 已完成
     */
    COMPLETED(2, "已完成"),

    /**
     * 已取消
     */
    CANCELLED(3, "已取消"),

    /**
     * 异常
     */
    ABNORMAL(4, "异常"),

    /**
     * 待确认：订单已创建，等待设备确认指令执行结果
     * <p>
     * 该状态用于混合模式启桩流程：订单创建后同步等待 3 秒，
     * 超时未收到设备响应时订单保持此状态，由异步补偿任务处理。
     * 设备确认后转为 CHARGING，超时后转为 CANCELLED。
     * </p>
     */
    PENDING_CONFIRM(5, "待确认");

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
    OrderStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据状态码获取对应的枚举
     *
     * @param code 状态码
     * @return 对应的订单状态枚举
     * @throws IllegalArgumentException 当状态码无效时抛出
     */
    public static OrderStatusEnum fromCode(int code) {
        for (OrderStatusEnum status : OrderStatusEnum.values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的订单状态码: " + code);
    }

    // ==================== Getters ====================

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
