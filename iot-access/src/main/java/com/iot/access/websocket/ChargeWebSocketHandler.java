package com.iot.access.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 充电 WebSocket 处理器
 * <p>
 * 处理充电桩/管理端 WebSocket 连接的生命周期事件。
 * 负责建立连接、接收文本消息、处理连接关闭等操作。
 * </p>
 */
@Slf4j
@Component
public class ChargeWebSocketHandler extends TextWebSocketHandler {

    /**
     * 连接建立后回调
     * <p>
     * 当客户端 WebSocket 连接成功建立时调用。
     * 记录连接日志，后续可用于会话管理、在线状态维护。
     * </p>
     *
     * @param session WebSocket 会话对象
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket 连接建立 - sessionId: {}", session.getId());
        // TODO: 实现连接建立后的处理逻辑
        // 1. 将 session 加入连接池管理
        // 2. 发送欢迎消息或初始化配置
        // 3. 更新设备在线状态
    }

    /**
     * 收到文本消息后回调
     * <p>
     * 当接收到客户端发送的文本消息时调用。
     * 解析消息内容并根据消息类型进行分发处理。
     * </p>
     *
     * @param session WebSocket 会话对象
     * @param message 接收到的文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.info("收到 WebSocket 消息 - sessionId: {}, payload: {}", session.getId(), message.getPayload());
        // TODO: 实现消息处理逻辑
        // 1. 解析 JSON 消息
        // 2. 根据消息类型调用对应业务逻辑
        // 3. 如需回复，通过 session.sendMessage() 返回
    }

    /**
     * 连接关闭后回调
     * <p>
     * 当 WebSocket 连接关闭（正常关闭或异常断开）时调用。
     * 清理会话信息、释放资源。
     * </p>
     *
     * @param session WebSocket 会话对象
     * @param status  关闭状态信息
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket 连接关闭 - sessionId: {}, status: {}", session.getId(), status);
        // TODO: 实现连接关闭后的处理逻辑
        // 1. 从连接池中移除 session
        // 2. 更新设备在线状态
        // 3. 触发离线通知
    }
}
