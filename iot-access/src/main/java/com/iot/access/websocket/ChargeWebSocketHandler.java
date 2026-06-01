package com.iot.access.websocket;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;

/**
 * 充电 WebSocket 处理器
 * <p>
 * 处理 WebSocket 连接的生命周期事件，负责从 URL 参数中提取用户信息，
 * 注册/注销 WebSocketSessionManager 会话，并处理客户端消息。
 * </p>
 * <p>
 * 连接 URL 格式：ws://host:9090/ws/charge?userId={userId}
 * 推送消息格式：{"type":"CHARGE_PROGRESS"|"DEVICE_STATUS"|"ALARM", "data":{...}, "timestamp":...}
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargeWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;

    /**
     * 连接建立后回调
     * <p>
     * 从 URL 查询参数中提取 userId，注册会话到 WebSocketSessionManager。
     * 如果未提供 userId 或解析失败，关闭连接并返回错误。
     * </p>
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = extractUserId(session);
        if (userId == null) {
            log.warn("[WS] 连接缺少 userId 参数，关闭连接 - sessionId: {}", session.getId());
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception ignored) {
            }
            return;
        }

        sessionManager.register(userId, session);
        log.info("[WS] 连接建立 - sessionId: {}, userId: {}", session.getId(), userId);

        // 发送欢迎消息
        Map<String, Object> welcomeMsg = Map.of(
                "type", "CONNECTED",
                "message", "连接成功",
                "timestamp", System.currentTimeMillis()
        );
        try {
            session.sendMessage(new TextMessage(JSONUtil.toJsonStr(welcomeMsg)));
        } catch (Exception e) {
            log.error("[WS] 发送欢迎消息失败 - sessionId: {}", session.getId(), e);
        }
    }

    /**
     * 收到文本消息后回调
     * <p>
     * 当前阶段客户端主要通过 WebSocket 接收推送，较少发送消息。
     * 如需发送消息，格式为：{"type":"PING"} → 回复 PONG。
     * </p>
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("[WS] 收到消息 - sessionId: {}, payload: {}", session.getId(), payload);

        try {
            JSONObject json = JSONUtil.parseObj(payload);
            String type = json.getStr("type");

            // 心跳检测：客户端发送 PING，服务端回复 PONG
            if ("PING".equals(type)) {
                Map<String, Object> pong = Map.of(
                        "type", "PONG",
                        "timestamp", System.currentTimeMillis()
                );
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pong)));
            }
        } catch (Exception e) {
            log.warn("[WS] 消息处理异常 - sessionId: {}", session.getId(), e);
        }
    }

    /**
     * 连接关闭后回调
     * <p>
     * 从 WebSocketSessionManager 中注销会话，清理资源。
     * </p>
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[WS] 连接关闭 - sessionId: {}, status: {}", session.getId(), status);
        sessionManager.unregister(session);
    }

    /**
     * 传输错误回调
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[WS] 传输错误 - sessionId: {}", session.getId(), exception);
        sessionManager.unregister(session);
    }

    /**
     * 从 WebSocket 会话 URL 中提取 userId
     * <p>
     * 解析 URL 查询参数，如 ws://host:9090/ws/charge?userId=123
     * </p>
     *
     * @param session WebSocket 会话
     * @return userId，解析失败返回 null
     */
    private Long extractUserId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) {
                return null;
            }
            String query = uri.getQuery();
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "userId".equals(kv[0])) {
                    return Long.parseLong(kv[1]);
                }
            }
        } catch (Exception e) {
            log.warn("[WS] 解析 userId 失败 - sessionId: {}", session.getId(), e);
        }
        return null;
    }
}
