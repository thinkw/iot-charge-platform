package com.iot.core.mq.consumer;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 设备事件消费者
 * <p>
 * 消费 RocketMQ device_event 主题的设备事件消息。
 * 当前阶段主要负责日志记录和异步处理，后续阶段将扩展：
 * - 设备上线/离线：更新运营大屏在线统计
 * - 状态变更：触发 WebSocket 推送
 * - 故障事件：触发告警通知
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "device_event",
        consumerGroup = "device_event_consumer",
        selectorExpression = "*"
)
public class DeviceEventConsumer implements RocketMQListener<String> {

    /**
     * 消费设备事件消息
     * <p>
     * 解析消息 JSON，根据事件类型处理：
     * - ONLINE：记录设备上线
     * - OFFLINE：记录设备离线
     * - STATUS_CHANGE：记录状态变更
     * - FAULT：触发告警处理
     * </p>
     *
     * @param message 消息内容（JSON字符串）
     */
    @Override
    public void onMessage(String message) {
        try {
            JSONObject json = JSONUtil.parseObj(message);
            String eventType = json.getStr("eventType");
            String sn = json.getStr("sn");
            Long chargerId = json.getLong("chargerId");

            log.info("[设备事件-消费] type: {}, SN: {}, chargerId: {}", eventType, sn, chargerId);

            switch (eventType) {
                case "ONLINE" -> handleOnlineEvent(sn, chargerId, json);
                case "OFFLINE" -> handleOfflineEvent(sn, chargerId, json);
                case "STATUS_CHANGE" -> handleStatusChangeEvent(sn, chargerId, json);
                default -> log.debug("[设备事件-消费] 未处理的事件类型: {}", eventType);
            }
        } catch (Exception e) {
            log.error("[设备事件-消费] 消息处理异常: {}", message, e);
        }
    }

    /**
     * 处理设备上线事件
     */
    private void handleOnlineEvent(String sn, Long chargerId, JSONObject json) {
        log.info("[设备事件] 设备上线 - SN: {}, chargerId: {}", sn, chargerId);
        // 后续阶段：推送 WebSocket 通知运营大屏更新在线统计
    }

    /**
     * 处理设备离线事件
     */
    private void handleOfflineEvent(String sn, Long chargerId, JSONObject json) {
        log.info("[设备事件] 设备离线 - SN: {}, chargerId: {}", sn, chargerId);
        // 后续阶段：推送 WebSocket 通知运营大屏
    }

    /**
     * 处理设备状态变更事件
     */
    private void handleStatusChangeEvent(String sn, Long chargerId, JSONObject json) {
        log.info("[设备事件] 设备状态变更 - SN: {}, chargerId: {}", sn, chargerId);
        // 后续阶段：推送 WebSocket 通知运营大屏实时更新设备状态
    }
}
