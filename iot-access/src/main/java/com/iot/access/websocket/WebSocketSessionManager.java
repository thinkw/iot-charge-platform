package com.iot.access.websocket;

import cn.hutool.json.JSONUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
     * WebSocket 发送线程池
     * <p>
     * 将同步阻塞的 WebSocket 发送操作异步化，避免慢客户端拖慢广播调用方线程。
     * 线程数为 CPU 核数×2（最少 4），守护线程不阻止 JVM 退出。
     * </p>
     */
    private final ExecutorService sendExecutor = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ws-send-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            }
    );

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
     * 向指定用户推送消息（异步发送）
     * <p>
     * 如果用户有多个连接，每个连接的发送以独立的异步任务提交到线程池执行。
     * 发送失败时重试一次（间隔 50ms），两次均失败则主动注销僵尸 session。
     * 调用方线程立即返回，不会被慢客户端阻塞。
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
                CompletableFuture.runAsync(() -> doSend(session, textMessage, userId), sendExecutor);
            }
        }
    }

    /**
     * 执行单次 WebSocket 发送，含一次重试和僵尸清理
     *
     * @param session  目标 WebSocket 会话
     * @param message  待发送消息
     * @param userId   用户ID（用于日志）
     */
    private void doSend(WebSocketSession session, TextMessage message, Long userId) {
        try {
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            // 第一次失败：50ms 后重试一次
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException e2) {
                log.warn("[WS推送] 发送失败(已重试) - userId: {}, sessionId: {}, error: {}",
                        userId, session.getId(), e2.getMessage());
                // 两次均失败 → 清理僵尸 session
                unregister(session);
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
     * 广播消息给所有连接的客户端（异步发送）
     * <p>
     * 遍历所有在线 session，每个发送以独立的异步任务提交到线程池执行。
     * 发送失败时重试一次（间隔 50ms），两次均失败则主动注销僵尸 session。
     * 调用方线程立即返回，不会被慢客户端阻塞。
     * </p>
     *
     * @param message 消息内容（JSON字符串）
     */
    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        userSessions.forEach((userId, sessions) -> {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    CompletableFuture.runAsync(() -> doSend(session, textMessage, userId), sendExecutor);
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

    /**
     * 优雅关闭发送线程池
     * <p>
     * 在 Spring Bean 销毁前调用，不再接受新任务，等待最多 5 秒
     * 让队列中的已提交任务执行完成。
     * </p>
     */
    @PreDestroy
    public void shutdown() {
        log.info("[WS会话] 正在关闭发送线程池...");
        sendExecutor.shutdown();
        try {
            if (!sendExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[WS会话] 发送线程池未在 5 秒内完全终止，强制关闭");
                sendExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendExecutor.shutdownNow();
        }
        log.info("[WS会话] 发送线程池已关闭");
    }
}
