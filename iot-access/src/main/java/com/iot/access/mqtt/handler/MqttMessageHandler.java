package com.iot.access.mqtt.handler;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.iot.access.mqtt.MqttSessionManager;
import com.iot.access.mqtt.codec.MqttMessage;
import com.iot.access.mqtt.codec.MqttMessageType;
import com.iot.core.service.DeviceService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MQTT 消息处理器（Netty ChannelHandler）
 * <p>
 * 处理 MQTT 客户端的生命周期事件和消息路由：
 * - channelActive：连接建立
 * - channelInactive：连接断开 → 触发 DeviceService.handleOffline
 * - channelRead0：接收 MQTT 消息并按 Topic 路由
 * - userEventTriggered：空闲检测（读超时 → 断连）
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class MqttMessageHandler extends SimpleChannelInboundHandler<MqttMessage> {

    /** MQTT Topic 前缀 */
    private static final String TOPIC_HEARTBEAT = "device/heartbeat";
    private static final String TOPIC_STATUS = "device/status";
    private static final String TOPIC_DATA = "device/data";
    private static final String TOPIC_ALARM = "device/alarm";
    private static final String TOPIC_COMMAND_RESPONSE = "device/command/response";

    private final DeviceService deviceService;
    private final MqttSessionManager sessionManager;

    /** 当前连接的 clientId（在 CONNECT 时设置） */
    private String clientId;

    // ==================== 连接生命周期 ====================

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("[MQTT] 新连接建立 - remoteAddress: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("[MQTT] 连接断开 - clientId: {}, remoteAddress: {}", clientId, ctx.channel().remoteAddress());
        // 触发设备离线处理
        if (clientId != null) {
            sessionManager.unregister(clientId);
            deviceService.handleOffline(clientId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("[MQTT] 连接异常 - clientId: {}", clientId, cause);
        ctx.close();
    }

    // ==================== 空闲检测 ====================

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("[MQTT] 读空闲超时，关闭连接 - clientId: {}", clientId);
                ctx.close();
            }
        }
    }

    // ==================== 消息路由 ====================

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
        MqttMessageType type = msg.getMessageType();

        switch (type) {
            case CONNECT -> handleConnect(ctx, msg);
            case PUBLISH -> handlePublish(ctx, msg);
            case PINGREQ -> handlePingreq(ctx);
            case DISCONNECT -> handleDisconnect(ctx);
            case PUBACK -> { /* PUBACK 由 Netty 内部处理，不需要业务处理 */ }
            default -> log.debug("[MQTT] 未处理的消息类型: {}", type);
        }
    }

    // ==================== CONNECT 处理 ====================

    /**
     * 处理 CONNECT 报文：设备鉴权
     * <p>
     * 提取 username(SN) 和 password(secret) 进行设备鉴权。
     * 鉴权通过：发送 CONNACK(0)，注册会话，触发上线
     * 鉴权失败：发送 CONNACK(4)（用户名密码错误），关闭连接
     * </p>
     */
    private void handleConnect(ChannelHandlerContext ctx, MqttMessage msg) {
        String sn = msg.getUsername();
        String secret = msg.getPasswordAsString();

        log.info("[MQTT] CONNECT - SN: {}, keepAlive: {}s", sn, msg.getKeepAlive());

        // 设备鉴权
        if (!deviceService.authenticateDevice(sn, secret)) {
            log.warn("[MQTT] 设备鉴权失败 - SN: {}", sn);
            ctx.writeAndFlush(MqttMessage.connack(4)); // 4 = Bad username or password
            ctx.close();
            return;
        }

        // 鉴权通过
        this.clientId = sn;
        ctx.writeAndFlush(MqttMessage.connack(0)); // 0 = Accepted

        // 注册会话
        sessionManager.register(sn, ctx.channel());

        // 触发设备上线
        deviceService.handleOnline(sn);
    }

    // ==================== PUBLISH 处理（Topic 路由） ====================

    /**
     * 处理 PUBLISH 报文：根据 Topic 路由到不同业务方法
     */
    private void handlePublish(ChannelHandlerContext ctx, MqttMessage msg) {
        String topic = msg.getTopic();
        String sn = this.clientId;
        String payload = msg.getPayloadAsString();

        if (sn == null) {
            log.warn("[MQTT] 未认证的设备尝试发布消息，忽略");
            return;
        }

        log.debug("[MQTT] PUBLISH - SN: {}, topic: {}, payload: {}", sn, topic, payload);

        try {
            switch (topic) {
                case TOPIC_HEARTBEAT -> deviceService.handleHeartbeat(sn);

                case TOPIC_STATUS -> {
                    JSONObject json = JSONUtil.parseObj(payload);
                    int status = json.getInt("status", 1);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = json.get("data", Map.class);
                    deviceService.handleStatusReport(sn, status, data);
                }

                case TOPIC_DATA -> {
                    JSONObject json = JSONUtil.parseObj(payload);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = json;
                    deviceService.handleDataReport(sn, data);
                }

                case TOPIC_ALARM -> {
                    JSONObject json = JSONUtil.parseObj(payload);
                    String alarmType = json.getStr("alarmType", "UNKNOWN");
                    int alarmLevel = json.getInt("alarmLevel", 1);
                    String content = json.getStr("content", "");
                    deviceService.handleAlarmReport(sn, alarmType, alarmLevel, content);
                }

                case TOPIC_COMMAND_RESPONSE -> {
                    // 指令响应：记录日志即可，业务方通过其他机制获取响应
                    log.info("[MQTT] 指令响应 - SN: {}, payload: {}", sn, payload);
                }

                default -> log.debug("[MQTT] 未知 topic: {}, 忽略", topic);
            }
        } catch (Exception e) {
            log.error("[MQTT] 处理 PUBLISH 消息异常 - SN: {}, topic: {}", sn, topic, e);
        }

        // QoS 1 需要回复 PUBACK
        if (msg.getQos() == 1) {
            ctx.writeAndFlush(MqttMessage.puback(msg.getPacketId()));
        }
    }

    // ==================== PINGREQ / DISCONNECT ====================

    /**
     * 处理心跳请求：回复 PINGRESP
     */
    private void handlePingreq(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(MqttMessage.pingresp());
    }

    /**
     * 处理断开连接请求
     */
    private void handleDisconnect(ChannelHandlerContext ctx) {
        log.info("[MQTT] DISCONNECT - SN: {}", clientId);
        ctx.close();
    }
}
