package com.iot.core.mq.consumer;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.iot.core.service.DeviceEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单事件消费者
 * <p>
 * 消费 RocketMQ order_event 主题的订单事件消息，
 * 通过 DeviceEventPublisher 向用户端推送订单状态变更和充电进度。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "order_event",
        consumerGroup = "order_event_consumer",
        selectorExpression = "*"
)
public class OrderEventConsumer implements RocketMQListener<String> {

    private final DeviceEventPublisher deviceEventPublisher;

    /**
     * 消费订单事件消息
     * <p>
     * 解析订单事件，根据事件类型向对应用户推送通知：
     * - CHARGE_STARTED：充电开始，通知用户充电已启动
     * - CHARGE_ENDED：充电结束，通知用户查看订单
     * - PAY_COMPLETED：支付完成，通知用户支付成功
     * </p>
     *
     * @param message 消息内容（JSON字符串）
     */
    @Override
    public void onMessage(String message) {
        try {
            JSONObject json = JSONUtil.parseObj(message);
            String eventType = json.getStr("eventType");
            Long userId = json.getLong("userId");
            String orderNo = json.getStr("orderNo");
            Long orderId = json.getLong("orderId");

            log.info("[订单事件-消费] type: {}, userId: {}, orderNo: {}", eventType, userId, orderNo);

            if (userId == null) {
                log.warn("[订单事件-消费] 消息缺少 userId，跳过推送: {}", message);
                return;
            }

            switch (eventType) {
                case "CHARGE_STARTED" -> pushChargeStarted(userId, orderNo, orderId);
                case "CHARGE_ENDED" -> pushChargeEnded(userId, orderNo, orderId, json);
                case "PAY_COMPLETED" -> pushPayCompleted(userId, orderNo, orderId);
                default -> log.debug("[订单事件-消费] 未处理的事件类型: {}", eventType);
            }
        } catch (Exception e) {
            log.error("[订单事件-消费] 消息处理异常: {}", message, e);
        }
    }

    /**
     * 推送充电开始通知
     */
    private void pushChargeStarted(Long userId, String orderNo, Long orderId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("orderId", orderId);
        data.put("message", "充电已开始");
        deviceEventPublisher.sendToUser(userId, "CHARGE_STARTED", data);
        log.info("[订单推送-充电开始] userId: {}, orderNo: {}", userId, orderNo);
    }

    /**
     * 推送充电结束通知
     */
    private void pushChargeEnded(Long userId, String orderNo, Long orderId, JSONObject json) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("orderId", orderId);
        data.put("message", "充电已结束，请查看订单");

        // 提取充电数据
        BigDecimal totalAmount = json.getBigDecimal("totalAmount");
        BigDecimal chargedEnergy = json.getBigDecimal("chargedEnergy");
        if (totalAmount != null) {
            data.put("totalAmount", totalAmount);
        }
        if (chargedEnergy != null) {
            data.put("chargedEnergy", chargedEnergy);
        }

        deviceEventPublisher.sendToUser(userId, "CHARGE_ENDED", data);
        log.info("[订单推送-充电结束] userId: {}, orderNo: {}, totalAmount: {}", userId, orderNo, totalAmount);
    }

    /**
     * 推送支付完成通知
     */
    private void pushPayCompleted(Long userId, String orderNo, Long orderId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("orderId", orderId);
        data.put("message", "支付成功");
        deviceEventPublisher.sendToUser(userId, "PAY_COMPLETED", data);
        log.info("[订单推送-支付完成] userId: {}, orderNo: {}", userId, orderNo);
    }
}
