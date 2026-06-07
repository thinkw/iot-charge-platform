package com.iot.api.event;

import com.iot.access.websocket.WebSocketSessionManager;
import com.iot.core.service.ChargeEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 充电事件推送实现
 * <p>
 * 实现 ChargeEventPublisher 接口，通过 WebSocketSessionManager
 * 将充电进度、开始、结束事件实时推送给对应的用户。
 * 作为 iot-api 模块的组件，桥接 iot-core（事件接口）和 iot-access（WebSocket）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChargeEventPublisher implements ChargeEventPublisher {

    private final WebSocketSessionManager sessionManager;

    /**
     * 推送充电进度更新
     */
    @Override
    public void publishChargeProgress(Long userId, String orderNo,
                                       BigDecimal chargedEnergy, BigDecimal currentPower,
                                       BigDecimal estimatedAmount, Long durationSeconds,
                                       BigDecimal voltage, BigDecimal current) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        // BigDecimal 用 toPlainString 防止 Hutool 序列化为科学计数法或被当成 0 丢字段
        data.put("chargedEnergy", chargedEnergy == null ? "0" : chargedEnergy.toPlainString());
        data.put("currentPower", currentPower == null ? "0" : currentPower.toPlainString());
        data.put("estimatedAmount", estimatedAmount == null ? "0" : estimatedAmount.toPlainString());
        data.put("durationSeconds", durationSeconds == null ? 0L : durationSeconds);
        if (voltage != null) {
            data.put("voltage", voltage.toPlainString());
        }
        if (current != null) {
            data.put("current", current.toPlainString());
        }

        sessionManager.sendToUser(userId, "CHARGE_PROGRESS", data);
        log.debug("[WS推送] 充电进度 - userId: {}, orderNo: {}, energy: {}, power: {}, fee: {}, duration: {}s",
                userId, orderNo, chargedEnergy, currentPower, estimatedAmount, durationSeconds);
    }

    /**
     * 推送充电开始通知
     */
    @Override
    public void publishChargeStart(Long userId, String orderNo, Long chargerId) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        data.put("chargerId", chargerId);
        data.put("message", "充电已开始");

        sessionManager.sendToUser(userId, "CHARGE_START", data);
        log.debug("[WS推送] 充电开始 - userId: {}, orderNo: {}", userId, orderNo);
    }

    /**
     * 推送充电结束通知
     * <p>
     * 兼容旧调用：未传 reason 时默认为 NORMAL（用户主动结束）。
     * 异常终止场景（如心跳超时自动终止）会传 ABNORMAL，前端据此展示不同文案。
     * </p>
     */
    @Override
    public void publishChargeStop(Long userId, String orderNo, BigDecimal totalAmount) {
        publishChargeStop(userId, orderNo, totalAmount, "NORMAL");
    }

    /**
     * 推送充电结束通知（带原因）
     *
     * @param reason NORMAL=用户主动结束 / ABNORMAL=异常自动终止（服务费折扣）
     */
    public void publishChargeStop(Long userId, String orderNo, BigDecimal totalAmount, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        data.put("totalAmount", totalAmount);
        data.put("reason", reason);
        data.put("message", "ABNORMAL".equals(reason)
                ? "设备异常已自动结算，请查看账单"
                : "充电已结束，请支付");

        sessionManager.sendToUser(userId, "CHARGE_STOP", data);
        log.debug("[WS推送] 充电停止 - userId: {}, orderNo: {}, amount: {}, reason: {}",
                userId, orderNo, totalAmount, reason);
    }

    /**
     * 推送指令执行状态通知
     * <p>
     * 用于向用户实时反馈指令执行进度。
     * WebSocket 消息类型为 COMMAND_STATUS。
     * </p>
     */
    @Override
    public void publishCommandStatus(Long userId, String orderNo, String status, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        data.put("status", status);
        data.put("message", message);

        sessionManager.sendToUser(userId, "COMMAND_STATUS", data);
        log.info("[WS推送] 指令状态 - userId: {}, orderNo: {}, status: {}, message: {}",
                userId, orderNo, status, message);
    }
}
