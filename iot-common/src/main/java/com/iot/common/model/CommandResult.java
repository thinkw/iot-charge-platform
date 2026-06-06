package com.iot.common.model;

/**
 * 指令同步等待结果枚举
 * <p>
 * 定义 ChargingServiceImpl 同步等待指令响应时的三种可能结果：
 * <ul>
 *   <li>{@link #SUCCESS} — 设备在超时时间内回复了成功响应</li>
 *   <li>{@link #DEVICE_ERROR} — 设备在超时时间内回复了错误响应</li>
 *   <li>{@link #TIMEOUT} — 同步等待超时，转入异步补偿</li>
 * </ul>
 * </p>
 *
 * @author IoT Team
 */
public enum CommandResult {

    /**
     * 设备执行成功 — 设备在同步等待时间内回复了 SUCCESS 响应，
     * 订单可从 PENDING_CONFIRM 转为 CHARGING
     */
    SUCCESS,

    /**
     * 设备拒绝执行 — 设备回复了 ERROR 响应（如设备故障、参数非法），
     * 订单应回滚/取消，向用户返回失败原因
     */
    DEVICE_ERROR,

    /**
     * 同步等待超时 — 在配置的同步等待时间内未收到设备响应，
     * 订单保持 PENDING_CONFIRM 状态，由 CommandTimeoutScheduler 异步补偿
     */
    TIMEOUT
}
