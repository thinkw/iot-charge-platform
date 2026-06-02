package com.iot.access.websocket;

import com.iot.core.service.DeviceEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket 设备事件发布器
 * <p>
 * 实现 DeviceEventPublisher 接口，通过 WebSocketSessionManager
 * 向客户端推送设备事件、告警通知和订单状态变更。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketDeviceEventPublisher implements DeviceEventPublisher {

    private final WebSocketSessionManager webSocketSessionManager;

    /**
     * 向所有连接的客户端广播消息
     *
     * @param type 消息类型
     * @param data 消息数据
     */
    @Override
    public void broadcast(String type, Object data) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", type);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        String json = cn.hutool.json.JSONUtil.toJsonStr(message);
        webSocketSessionManager.broadcast(json);
    }

    /**
     * 向指定用户推送消息
     *
     * @param userId 目标用户ID
     * @param type   消息类型
     * @param data   消息数据
     */
    @Override
    public void sendToUser(Long userId, String type, Object data) {
        webSocketSessionManager.sendToUser(userId, type, data);
    }
}
