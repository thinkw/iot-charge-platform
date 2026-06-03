package com.iot.core.mq.producer;

import com.iot.core.entity.ChargeOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 充电事件 RocketMQ 生产者
 * <p>
 * 将充电开始、充电结束、支付完成等事件发送到 RocketMQ，
 * 供运营后台大屏、告警系统等消费者使用。
 * 消息发送采用 best-effort 策略，失败不影响主流程。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargeEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /** 订单事件 Topic */
    private static final String TOPIC_ORDER_EVENT = "order_event";

    /**
     * 发送充电开始事件
     *
     * @param order 充电订单
     */
    public void publishChargeStartEvent(ChargeOrder order) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CHARGE_STARTED");
        event.put("orderNo", order.getOrderNo());
        event.put("orderId", order.getId());
        event.put("userId", order.getUserId());
        event.put("chargerId", order.getChargerId());
        event.put("stationId", order.getStationId());
        event.put("timestamp", System.currentTimeMillis());

        sendEvent(event);
    }

    /**
     * 发送充电结束事件
     *
     * @param order 充电订单
     */
    public void publishChargeEndEvent(ChargeOrder order) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CHARGE_ENDED");
        event.put("orderNo", order.getOrderNo());
        event.put("orderId", order.getId());
        event.put("userId", order.getUserId());
        event.put("chargerId", order.getChargerId());
        event.put("stationId", order.getStationId());
        event.put("chargedEnergy", order.getChargedEnergy());
        event.put("totalAmount", order.getTotalAmount());
        event.put("timestamp", System.currentTimeMillis());

        sendEvent(event);
    }

    /**
     * 发送支付完成事件
     *
     * @param order 充电订单（需携带 userId 以支持 WebSocket 精准推送）
     */
    public void publishPayCompletedEvent(ChargeOrder order) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "PAY_COMPLETED");
        event.put("orderNo", order.getOrderNo());
        event.put("orderId", order.getId());
        event.put("userId", order.getUserId());
        event.put("amount", order.getTotalAmount());
        event.put("timestamp", System.currentTimeMillis());

        sendEvent(event);
    }

    /**
     * 发送事件到 RocketMQ（best-effort）
     */
    private void sendEvent(Map<String, Object> event) {
        try {
            rocketMQTemplate.convertAndSend(TOPIC_ORDER_EVENT, event);
            log.debug("[MQ] 发送订单事件成功 - eventType: {}", event.get("eventType"));
        } catch (Exception e) {
            log.warn("[MQ] 发送订单事件失败 - eventType: {}, error: {}",
                    event.get("eventType"), e.getMessage());
        }
    }
}
