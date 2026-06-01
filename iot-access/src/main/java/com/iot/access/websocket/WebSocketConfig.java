package com.iot.access.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类
 * <p>
 * 配置 WebSocket 处理器和连接路径，启用手持设备/管理端的实时通信通道。
 * 注册的 WebSocket 处理器位于路径 "/ws/charge"，用于充电桩状态实时推送。
 * </p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChargeWebSocketHandler chargeWebSocketHandler;

    public WebSocketConfig(ChargeWebSocketHandler chargeWebSocketHandler) {
        this.chargeWebSocketHandler = chargeWebSocketHandler;
    }

    /**
     * 注册 WebSocket 处理器
     * <p>
     * 将充电 WebSocket 处理器注册到 "/ws/charge" 路径，
     * 允许所有来源的跨域连接（开发模式），生产环境需限制具体域名。
     * </p>
     *
     * @param registry WebSocket 处理器注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chargeWebSocketHandler, "/ws/charge")
                .setAllowedOrigins("*");
    }
}
