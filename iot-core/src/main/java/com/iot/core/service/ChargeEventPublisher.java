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
     * @param voltage         当前电压(V)，可能为 null（后端暂未透传时）
     * @param current         当前电流(A)，可能为 null
     */
    void publishChargeProgress(Long userId, String orderNo,
                               java.math.BigDecimal chargedEnergy,
                               java.math.BigDecimal currentPower,
                               java.math.BigDecimal estimatedAmount,
                               Long durationSeconds,
                               java.math.BigDecimal voltage,
                               java.math.BigDecimal current);

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

    /**
     * 推送充电结束通知（带原因）
     * <p>
     * 默认实现：未识别 reason 时复用 {@link #publishChargeStop}，前端通过消息体
     * 中的 reason 字段（NORMAL / ABNORMAL）区分正常结束与异常终止。
     * </p>
     *
     * @param userId      用户ID
     * @param orderNo     订单编号
     * @param totalAmount 总金额(元)
     * @param reason      NORMAL=用户主动结束 / ABNORMAL=异常自动终止（服务费折扣）
     */
    default void publishChargeStop(Long userId, String orderNo, java.math.BigDecimal totalAmount, String reason) {
        publishChargeStop(userId, orderNo, totalAmount);
    }

    /**
     * 推送指令执行状态通知
     * <p>
     * 用于混合模式启桩流程中告知用户指令执行进度。
     * 在以下场景触发：
     * <ul>
     *   <li>同步等待超时 → 推送 "PENDING" 状态，告知用户设备正在启动中</li>
     *   <li>异步补偿超时 → 推送 "TIMEOUT" 状态，告知用户启动失败</li>
     *   <li>对账确认成功 → 推送 "SUCCESS" 状态，告知用户充电已开始</li>
     * </ul>
     * </p>
     *
     * @param userId  用户ID
     * @param orderNo 订单编号
     * @param status  指令状态（PENDING/SUCCESS/FAILED/TIMEOUT）
     * @param message 提示消息（面向用户展示）
     */
    void publishCommandStatus(Long userId, String orderNo, String status, String message);
}
