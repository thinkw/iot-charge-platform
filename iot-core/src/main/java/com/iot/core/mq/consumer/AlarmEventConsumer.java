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
 * 告警事件消费者
 * <p>
 * 消费 RocketMQ alarm_event 主题的告警事件消息，
 * 通过 DeviceEventPublisher 实时推送到运营后台，实现告警弹窗提醒。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.consumer.enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "alarm_event",
        consumerGroup = "alarm_event_consumer",
        selectorExpression = "*"
)
public class AlarmEventConsumer implements RocketMQListener<String> {

    private final DeviceEventPublisher deviceEventPublisher;

    /**
     * 消费告警事件消息
     * <p>
     * 解析告警消息，构建推送数据，通过 WebSocket 广播到所有连接的运营端。
     * 消息结构参考 DESIGN.md 中定义的告警事件格式。
     * </p>
     *
     * @param message 消息内容（JSON字符串）
     */
    @Override
    public void onMessage(String message) {
        try {
            JSONObject json = JSONUtil.parseObj(message);
            String eventType = json.getStr("eventType");
            Long alarmId = json.getLong("alarmId");
            Long chargerId = json.getLong("chargerId");
            Long stationId = json.getLong("stationId");
            String alarmType = json.getStr("alarmType");
            Integer alarmLevel = json.getInt("alarmLevel");
            String content = json.getStr("content");

            log.info("[告警事件-消费] type: {}, alarmId: {}, chargerId: {}, alarmType: {}, alarmLevel: {}",
                    eventType, alarmId, chargerId, alarmType, alarmLevel);

            if ("ALARM_CREATED".equals(eventType)) {
                // 通过 DeviceEventPublisher 广播到所有 WebSocket 连接
                deviceEventPublisher.broadcast("ALARM",
                        buildAlarmData(alarmId, chargerId, stationId, alarmType, alarmLevel, content));
                log.info("[告警推送] 告警已广播 - alarmId: {}, chargerId: {}", alarmId, chargerId);
            }
        } catch (Exception e) {
            log.error("[告警事件-消费] 消息处理异常: {}", message, e);
        }
    }

    /**
     * 构建告警推送数据
     */
    private Map<String, Object> buildAlarmData(Long alarmId, Long chargerId, Long stationId,
                                                String alarmType, Integer alarmLevel, String content) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alarmId", alarmId);
        data.put("chargerId", chargerId);
        data.put("stationId", stationId);
        data.put("alarmType", alarmType);
        data.put("alarmLevel", alarmLevel);
        data.put("alarmLevelDesc", getAlarmLevelDesc(alarmLevel));
        data.put("content", content);
        return data;
    }

    /**
     * 获取告警级别中文描述
     */
    private String getAlarmLevelDesc(Integer level) {
        if (level == null) return "未知";
        return switch (level) {
            case 1 -> "一般";
            case 2 -> "重要";
            case 3 -> "紧急";
            default -> "未知";
        };
    }
}
