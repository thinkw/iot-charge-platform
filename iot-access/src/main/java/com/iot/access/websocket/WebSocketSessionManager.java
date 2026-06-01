package com.iot.access.websocket;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 会话管理器
 * <p>
 * 管理用户 WebSocket 连接，维护 userId 与 WebSocketSession 的映射关系。
 * 支持按用户推送消息、广播消息、会话统计等功能。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    /** userId → Set<WebSocketSession> 映射 */
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /** sessionId → userId 反向映射，用于连接关闭时清理 */
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    /**
     * 注册 WebSocket 会话
     * <p>
     * 连接建立时调用，将 session 与 userId 绑定。
     * 一个用户可以有多个 WebSocket 连接（如多端登录）。
     * </p>
     *
     * @param userId  用户ID
     * @param session WebSocket 会话
     */
    public void register(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionUserMap.put(session.getId(), userId);
        log.info("[WS会话] 注册 - userId: {}, sessionId: {}, 用户连接数: {}",
                userId, session.getId(), userSessions.get(userId).size());
    }

    /**
     * 注销 WebSocket 会话
     * <p>
     * 连接关闭时调用，清理 userId 和 session 的映射关系。
     * </p>
     *
     * @param session WebSocket 会话
     */
    public void unregister(WebSocketSession session) {
        Long userId = sessionUserMap.remove(session.getId());
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        log.info("[WS会话] 注销 - sessionId: {}, userId: {}", session.getId(), userId);
    }

    /**
     * 向指定用户推送消息
     * <p>
     * 如果用户有多个连接，消息会发送到所有连接。
     * 发送失败（如连接已断开）不抛出异常，仅记录警告日志。
     * </p>
     *
     * @param userId  目标用户ID
     * @param message 消息内容（JSON字符串）
     */
    public void sendToUser(Long userId, String message) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.warn("[WS推送] 发送失败 - userId: {}, sessionId: {}, error: {}",
                            userId, session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 向指定用户推送类型化消息
     *
     * @param userId 目标用户ID
     * @param type   消息类型
     * @param data   消息数据
     */
    public void sendToUser(Long userId, String type, Object data) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", type);
        msg.put("data", data);
        msg.put("timestamp", System.currentTimeMillis());
        sendToUser(userId, JSONUtil.toJsonStr(msg));
    }

    /**
     * 广播消息给所有连接的客户端
     *
     * @param message 消息内容（JSON字符串）
     */
    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        userSessions.forEach((userId, sessions) -> {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(textMessage);
                        }
                    } catch (IOException e) {
                        log.warn("[WS广播] 发送失败 - userId: {}, sessionId: {}", userId, session.getId());
                    }
                }
            }
        });
    }

    /**
     * 获取当前在线连接数
     *
     * @return 总连接数
     */
    public int getConnectionCount() {
        return sessionUserMap.size();
    }

    /**
     * 获取当前在线用户数
     *
     * @return 在线用户数
     */
    public int getOnlineUserCount() {
        return userSessions.size();
    }
}
