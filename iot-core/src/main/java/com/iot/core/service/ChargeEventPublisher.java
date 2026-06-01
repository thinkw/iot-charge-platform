package com.iot.core.service;

/**
 * 充电事件推送接口
 * <p>
 * 定义充电过程中的事件推送抽象，由 iot-api 模块通过 WebSocket 实现。
 * 使用接口隔离 iot-core 与接入层的依赖。
 * </p>
 *
 * @author IoT Team
 */
public interface ChargeEventPublisher {

    /**
     * 推送充电进度更新
     *
     * @param userId          用户ID
     * @param orderNo         订单编号
     * @param chargedEnergy   已充电量(kWh)
     * @param currentPower    当前功率(kW)
     * @param estimatedAmount 估算费用(元)
     * @param durationSeconds 已充时长(秒)
     */
    void publishChargeProgress(Long userId, String orderNo,
                               java.math.BigDecimal chargedEnergy,
                               java.math.BigDecimal currentPower,
                               java.math.BigDecimal estimatedAmount,
                               Long durationSeconds);

    /**
     * 推送充电开始通知
     *
     * @param userId    用户ID
     * @param orderNo   订单编号
     * @param chargerId 充电桩ID
     */
    void publishChargeStart(Long userId, String orderNo, Long chargerId);

    /**
     * 推送充电结束通知
     *
     * @param userId      用户ID
     * @param orderNo     订单编号
     * @param totalAmount 总金额(元)
     */
    void publishChargeStop(Long userId, String orderNo, java.math.BigDecimal totalAmount);
}
