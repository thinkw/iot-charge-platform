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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备事件消费者
 * <p>
 * 消费 RocketMQ device_event 主题的设备事件消息，
 * 通过 DeviceEventPublisher 向运营大屏推送设备上下线和状态变更通知。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "device_event",
        consumerGroup = "device_event_consumer",
        selectorExpression = "*"
)
public class DeviceEventConsumer implements RocketMQListener<String> {

    private final DeviceEventPublisher deviceEventPublisher;

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
     * <p>
     * 通过 WebSocket 广播设备上线通知到运营大屏。
     * </p>
     */
    private void handleOnlineEvent(String sn, Long chargerId, JSONObject json) {
        log.info("[设备事件] 设备上线 - SN: {}, chargerId: {}", sn, chargerId);
        deviceEventPublisher.broadcast("DEVICE_ONLINE", buildEventPayload(sn, chargerId, json));
    }

    /**
     * 处理设备离线事件
     * <p>
     * 通过 DeviceEventPublisher 广播设备离线通知到运营大屏。
     * </p>
     */
    private void handleOfflineEvent(String sn, Long chargerId, JSONObject json) {
        log.info("[设备事件] 设备离线 - SN: {}, chargerId: {}", sn, chargerId);
        deviceEventPublisher.broadcast("DEVICE_OFFLINE", buildEventPayload(sn, chargerId, json));
    }

    /**
     * 处理设备状态变更事件
     * <p>
     * 通过 DeviceEventPublisher 广播设备状态变更通知到运营大屏。
     * </p>
     */
    private void handleStatusChangeEvent(String sn, Long chargerId, JSONObject json) {
        log.info("[设备事件] 设备状态变更 - SN: {}, chargerId: {}", sn, chargerId);
        deviceEventPublisher.broadcast("DEVICE_STATUS_CHANGE", buildEventPayload(sn, chargerId, json));
    }

    /**
     * 构建事件载荷
     */
    private Map<String, Object> buildEventPayload(String sn, Long chargerId, JSONObject json) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sn", sn);
        payload.put("chargerId", chargerId);

        // 提取设备状态信息
        JSONObject dataJson = json.getJSONObject("data");
        if (dataJson != null) {
            Integer status = dataJson.getInt("status");
            if (status != null) {
                payload.put("status", status);
                payload.put("statusDesc", getStatusDesc(status));
            }
            // 提取实时数据（电压、电流、功率等）
            Object voltage = dataJson.get("voltage");
            Object current = dataJson.get("current");
            Object power = dataJson.get("power");
            Object energy = dataJson.get("energy");
            Object temperature = dataJson.get("temperature");
            if (voltage != null) payload.put("voltage", voltage);
            if (current != null) payload.put("current", current);
            if (power != null) payload.put("power", power);
            if (energy != null) payload.put("energy", energy);
            if (temperature != null) payload.put("temperature", temperature);
        }
        // 提取充电站信息
        Long stationId = json.getLong("stationId");
        if (stationId != null) {
            payload.put("stationId", stationId);
        }
        return payload;
    }

    /**
     * 获取状态码对应的中文描述
     */
    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "离线";
            case 1 -> "空闲";
            case 2 -> "充电中";
            case 3 -> "故障";
            case 4 -> "锁定";
            default -> "未知";
        };
    }
}
