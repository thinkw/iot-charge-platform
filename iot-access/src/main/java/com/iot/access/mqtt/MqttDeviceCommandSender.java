package com.iot.access.mqtt;

import cn.hutool.json.JSONUtil;
import com.iot.access.mqtt.codec.MqttMessage;
import com.iot.access.mqtt.command.CommandResponseManager;
import com.iot.common.model.CommandResult;
import com.iot.common.util.SnowflakeIdUtil;
import com.iot.core.service.DeviceCommandSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MQTT 设备指令下发实现
 * <p>
 * 实现 {@link DeviceCommandSender} 接口，通过 MQTT 协议向设备下发远程控制指令。
 * 指令发布到 topic: device/command/{sn}，QoS 1（至少一次送达）。
 * </p>
 * <p>
 * <b>v2 增强</b>：新增指令 ID 生成、MQTT packetId 追踪、指令响应匹配支持。
 * 每条指令携带唯一 commandId（SnowflakeId），设备响应时通过 commandId 关联。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
public class MqttDeviceCommandSender implements DeviceCommandSender {

    /** 指令下发 Topic 前缀 */
    private static final String COMMAND_TOPIC_PREFIX = "device/command/";

    /** MQTT packetId 自增计数器（范围 1-65535，MQTT 协议规定 packetId 不能为 0） */
    private final AtomicInteger packetIdCounter = new AtomicInteger(1);

    private final MqttSessionManager sessionManager;
    private final CommandResponseManager commandResponseManager;

    public MqttDeviceCommandSender(MqttSessionManager sessionManager,
                                   CommandResponseManager commandResponseManager) {
        this.sessionManager = sessionManager;
        this.commandResponseManager = commandResponseManager;
    }

    // ==================== v2 带确认的指令下发 ====================

    /**
     * 向指定设备下发指令（带响应追踪）
     * <p>
     * 与 {@link #sendCommand(String, String, Map)} 相比，此方法：
     * <ul>
     *   <li>生成唯一 commandId（SnowflakeId）用于响应匹配</li>
     *   <li>分配 MQTT packetId 用于 QoS 1 握手追踪</li>
     *   <li>在消息体中携带 commandId，供设备响应时回传</li>
     *   <li>将指令详情注册到 CommandResponseManager 用于后续状态追踪</li>
     * </ul>
     * </p>
     *
     * @param sn      设备唯一序列号
     * @param command 指令类型（如 START_CHARGE、STOP_CHARGE）
     * @param params  指令参数
     * @param orderNo 关联订单号（可选，非指令关联场景可为 null）
     * @param userId  操作用户ID（可选，非用户操作场景可为 null）
     * @return commandId 如果发送成功，null 如果设备不在线或发送失败
     */
    public String sendCommand(String sn, String command, Map<String, Object> params,
                              String orderNo, Long userId) {
        if (!sessionManager.isOnline(sn)) {
            log.warn("[指令下发] 设备不在线 - SN: {}", sn);
            return null;
        }

        // 1. 生成唯一 commandId
        String commandId = SnowflakeIdUtil.nextIdStr();

        // 2. 分配 MQTT packetId
        int packetId = allocatePacketId();

        // 3. 构建指令消息（携带 commandId）
        byte[] payload = buildCommandPayload(commandId, command, params);

        // 4. 注册指令到 CommandResponseManager（异步模式，仅记录状态）
        commandResponseManager.registerAsync(commandId, sn, command, params, orderNo, userId, packetId);

        // 5. 通过 MQTT 发送指令
        String topic = COMMAND_TOPIC_PREFIX + sn;
        MqttMessage message = MqttMessage.publishWithId(topic, payload, 1, packetId);
        boolean success = sessionManager.sendMessage(sn, message);

        if (!success) {
            log.warn("[指令下发] 消息发送失败 - SN: {}, commandId: {}, 指令: {}", sn, commandId, command);
            // 发送失败但已注册到 Redis，由定时任务检测并处理
        }

        log.info("[指令下发] SN: {}, commandId: {}, 指令: {}, packetId: {}, 结果: {}",
                sn, commandId, command, packetId, success ? "成功" : "失败");
        return commandId;
    }

    /**
     * 重发已有指令（供 CommandTimeoutScheduler 调用）
     * <p>
     * 保持原有 commandId 不变，分配新的 packetId。
     * 设备通过 commandId 去重，避免重复执行。
     * </p>
     *
     * @param commandId 原指令唯一ID
     * @param sn        设备唯一序列号
     * @param command   指令类型
     * @param params    指令参数
     * @return 新的 MQTT packetId（用于后续更新 Redis 重试信息），0 表示发送失败
     */
    public int resendCommand(String commandId, String sn, String command, Map<String, Object> params) {
        if (!sessionManager.isOnline(sn)) {
            log.warn("[指令重发] 设备不在线 - SN: {}, commandId: {}", sn, commandId);
            return 0;
        }

        // 1. 分配新的 packetId（保持 commandId 不变，用于去重）
        int newPacketId = allocatePacketId();

        // 2. 构建指令消息（使用相同 commandId）
        byte[] payload = buildCommandPayload(commandId, command, params);

        // 3. 下发重发指令
        String topic = COMMAND_TOPIC_PREFIX + sn;
        MqttMessage message = MqttMessage.publishWithId(topic, payload, 1, newPacketId);
        boolean success = sessionManager.sendMessage(sn, message);

        if (success) {
            log.info("[指令重发] commandId: {}, SN: {}, 指令: {}, 新packetId: {}, 结果: 成功",
                    commandId, sn, command, newPacketId);
            return newPacketId;
        } else {
            log.warn("[指令重发] 发送失败 - commandId: {}, SN: {}", commandId, sn);
            return 0;
        }
    }

    /**
     * 下发指令并同步等待设备响应（混合模式核心方法）
     * <p>
     * 生成 commandId → 注册到 CommandResponseManager → 下发 MQTT 指令 → 阻塞等待响应。
     * 同步等待超时后返回 TIMEOUT，异步补偿由 CommandTimeoutScheduler 继续处理。
     * </p>
     *
     * @param sn        设备唯一序列号
     * @param command   指令类型
     * @param params    指令参数
     * @param orderNo   关联订单号
     * @param userId    操作用户ID
     * @param timeoutMs 同步等待超时（毫秒）
     * @return 指令执行结果
     */
    @Override
    public CommandResult sendCommandAndWait(String sn, String command, Map<String, Object> params,
                                            String orderNo, Long userId, long timeoutMs) {
        if (!sessionManager.isOnline(sn)) {
            log.warn("[指令下发-同步] 设备不在线 - SN: {}", sn);
            return null; // null 表示设备不在线，需要调用方特殊处理
        }

        // 1. 生成唯一 commandId
        String commandId = SnowflakeIdUtil.nextIdStr();

        // 2. 分配 MQTT packetId
        int packetId = allocatePacketId();

        // 3. 构建指令消息
        byte[] payload = buildCommandPayload(commandId, command, params);

        // 4. 下发 MQTT 指令
        String topic = COMMAND_TOPIC_PREFIX + sn;
        MqttMessage message = MqttMessage.publishWithId(topic, payload, 1, packetId);
        boolean success = sessionManager.sendMessage(sn, message);

        if (!success) {
            log.warn("[指令下发-同步] 消息发送失败 - SN: {}, commandId: {}", sn, commandId);
            return null;
        }

        log.info("[指令下发-同步] SN: {}, commandId: {}, 指令: {}, 等待 {}ms",
                sn, commandId, command, timeoutMs);

        // 5. 注册并同步等待响应（CommandResponseManager 内部管理 Future）
        return commandResponseManager.registerAndWait(
                commandId, sn, command, params, orderNo, userId, packetId, timeoutMs);
    }

    // ==================== v1 兼容接口 ====================

    /**
     * 向指定设备下发指令（v1 兼容接口，不带响应追踪）
     * <p>
     * 保留此方法以兼容 stopCharge 等不需要同步等待的场景。
     * 内部自动生成 commandId 和 packetId 以保持链路一致性。
     * </p>
     *
     * @param sn      设备唯一序列号
     * @param command 指令类型（如 START_CHARGE、STOP_CHARGE、RESTART）
     * @param params  指令参数
     * @return true 如果指令发送成功，false 如果设备不在线
     */
    @Override
    public boolean sendCommand(String sn, String command, Map<String, Object> params) {
        // 使用 v2 接口实现，但不传 orderNo 和 userId
        String commandId = sendCommand(sn, command, params, null, null);
        return commandId != null;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建指令消息的 JSON 负载
     * <p>
     * 消息格式：
     * <pre>
     * {
     *   "commandId": "雪花ID",
     *   "command": "START_CHARGE",
     *   "params": {...},
     *   "timestamp": 1717670400000
     * }
     * </pre>
     * commandId 用于设备侧去重和服务端响应匹配。
     * </p>
     *
     * @param commandId 指令唯一ID
     * @param command   指令类型
     * @param params    指令参数
     * @return JSON 字节数组
     */
    private byte[] buildCommandPayload(String commandId, String command, Map<String, Object> params) {
        Map<String, Object> commandMsg = new HashMap<>();
        commandMsg.put("commandId", commandId);
        commandMsg.put("command", command);
        commandMsg.put("params", params != null ? params : new HashMap<>());
        commandMsg.put("timestamp", System.currentTimeMillis());

        return JSONUtil.toJsonStr(commandMsg).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 分配 MQTT packetId
     * <p>
     * MQTT 3.1.1 协议规定 packetId 为 16 位无符号整数（1-65535），0 为无效值。
     * 使用自增计数器循环分配，正常情况下不会短时间内耗尽。
     * </p>
     *
     * @return 有效的 packetId（范围 1-65535）
     */
    private int allocatePacketId() {
        int id = packetIdCounter.incrementAndGet() & 0xFFFF;  // 取低 16 位
        if (id == 0) {
            id = packetIdCounter.incrementAndGet() & 0xFFFF;  // 跳过 0
        }
        return id;
    }
}
