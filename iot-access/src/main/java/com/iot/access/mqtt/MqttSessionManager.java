package com.iot.access.mqtt;

import com.iot.access.mqtt.codec.MqttMessage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 会话管理器
 * <p>
 * 管理 MQTT 客户端连接的生命周期，维护 clientId 与 Netty Channel 的映射关系。
 * 提供：
 * - 会话注册/注销
 * - 按 SN（即 clientId）查找客户端 Channel
 * - 向指定设备发送 MQTT 消息
 * - 在线设备统计
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
public class MqttSessionManager {

    /** clientId → Channel 映射，线程安全 */
    private final Map<String, Channel> sessionMap = new ConcurrentHashMap<>();

    /**
     * 注册会话
     * <p>
     * 设备 CONNECT 成功后调用，将 clientId 与 Channel 绑定。
     * 如果同一 clientId 已有旧连接，先关闭旧连接。
     * </p>
     *
     * @param clientId 客户端标识（设备SN）
     * @param channel  Netty Channel
     */
    public void register(String clientId, Channel channel) {
        Channel oldChannel = sessionMap.put(clientId, channel);
        if (oldChannel != null && oldChannel.isActive()) {
            log.info("[会话管理] 关闭旧连接 - clientId: {}", clientId);
            oldChannel.close();
        }
        log.info("[会话管理] 注册会话 - clientId: {}, 在线设备数: {}", clientId, sessionMap.size());
    }

    /**
     * 注销会话
     * <p>
     * 设备断开连接时调用，从映射中移除。
     * </p>
     *
     * @param clientId 客户端标识
     */
    public void unregister(String clientId) {
        sessionMap.remove(clientId);
        log.info("[会话管理] 注销会话 - clientId: {}, 在线设备数: {}", clientId, sessionMap.size());
    }

    /**
     * 根据 clientId 查找 Channel
     *
     * @param clientId 客户端标识
     * @return Channel，如果不在线则返回 null
     */
    public Channel getChannel(String clientId) {
        return sessionMap.get(clientId);
    }

    /**
     * 判断设备是否在线（有活跃连接）
     *
     * @param clientId 客户端标识
     * @return true 如果设备有活跃 MQTT 连接
     */
    public boolean isOnline(String clientId) {
        Channel channel = sessionMap.get(clientId);
        return channel != null && channel.isActive();
    }

    /**
     * 向指定设备发送 MQTT 消息
     * <p>
     * 如果设备不在线或无活跃连接，返回 false。
     * 发送操作异步执行，不阻塞调用线程。
     * </p>
     *
     * @param clientId 客户端标识（设备SN）
     * @param message  待发送的 MQTT 消息
     * @return true 消息已提交发送，false 设备不在线
     */
    public boolean sendMessage(String clientId, MqttMessage message) {
        Channel channel = sessionMap.get(clientId);
        if (channel == null || !channel.isActive()) {
            log.warn("[会话管理] 设备不在线，无法发送消息 - clientId: {}", clientId);
            return false;
        }

        ChannelFuture future = channel.writeAndFlush(message);
        future.addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                log.error("[会话管理] 消息发送失败 - clientId: {}, error: {}", clientId, f.cause().getMessage());
            }
        });

        return true;
    }

    /**
     * 获取当前在线设备数量
     *
     * @return 在线设备数
     */
    public int getOnlineCount() {
        return sessionMap.size();
    }

    /**
     * 关闭所有会话
     * <p>
     * 应用关闭时调用，优雅关闭所有 MQTT 连接。
     * </p>
     */
    public void closeAll() {
        log.info("[会话管理] 开始关闭所有会话，共 {} 个", sessionMap.size());
        sessionMap.forEach((clientId, channel) -> {
            try {
                if (channel.isActive()) {
                    channel.close();
                }
            } catch (Exception e) {
                log.error("[会话管理] 关闭会话失败 - clientId: {}", clientId, e);
            }
        });
        sessionMap.clear();
        log.info("[会话管理] 所有会话已关闭");
    }

    /**
     * 根据 ChannelId 查找 Channel（遍历 sessionMap，O(n)）
     *
     * @param channelId Netty ChannelId
     * @return Channel，如果未找到返回 null
     */
    public Channel getChannelById(ChannelId channelId) {
        for (Channel ch : sessionMap.values()) {
            if (ch.id().equals(channelId)) {
                return ch;
            }
        }
        return null;
    }

    public Collection<Channel> getAllChannels(){
        return sessionMap.values();
    }
}
