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
                                       BigDecimal estimatedAmount, Long durationSeconds) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        data.put("chargedEnergy", chargedEnergy);
        data.put("currentPower", currentPower);
        data.put("estimatedAmount", estimatedAmount);
        data.put("durationSeconds", durationSeconds);

        sessionManager.sendToUser(userId, "CHARGE_PROGRESS", data);
        log.debug("[WS推送] 充电进度 - userId: {}, orderNo: {}", userId, orderNo);
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
     */
    @Override
    public void publishChargeStop(Long userId, String orderNo, BigDecimal totalAmount) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        data.put("totalAmount", totalAmount);
        data.put("message", "充电已结束，请支付");

        sessionManager.sendToUser(userId, "CHARGE_STOP", data);
        log.debug("[WS推送] 充电停止 - userId: {}, orderNo: {}, amount: {}",
                userId, orderNo, totalAmount);
    }
}
