package com.iot.core.service;

/**
 * 设备事件发布器接口
 * <p>
 * 定义向 WebSocket 推送消息的抽象接口，在 iot-core 中定义，
 * 由 iot-access 模块中的 WebSocketDeviceEventPublisher 实现。
 * 这样 MQ 消费者可以依赖此接口而不直接依赖 WebSocket 实现，
 * 遵循依赖倒置原则，避免模块间的循环依赖。
 * </p>
 *
 * @author IoT Team
 */
public interface DeviceEventPublisher {

    /**
     * 向所有连接的客户端广播消息
     *
     * @param type 消息类型
     * @param data 消息数据
     */
    void broadcast(String type, Object data);

    /**
     * 向指定用户推送消息
     *
     * @param userId 目标用户ID
     * @param type   消息类型
     * @param data   消息数据
     */
    void sendToUser(Long userId, String type, Object data);
}
