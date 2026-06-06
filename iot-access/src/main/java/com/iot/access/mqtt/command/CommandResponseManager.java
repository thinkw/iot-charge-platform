package com.iot.access.mqtt.command;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.iot.common.enums.CommandStatusEnum;
import com.iot.common.model.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 指令响应管理器
 * <p>
 * MQTT 指令可靠性的核心组件，负责指令生命周期的全程管理：
 * <ul>
 *   <li><b>注册</b>：指令下发前将指令详情写入 Redis，同时在本地内存注册 CompletableFuture</li>
 *   <li><b>同步等待</b>：通过 CompletableFuture 在 HTTP 线程中阻塞等待设备响应</li>
 *   <li><b>响应匹配</b>：设备回复 command/response 时，通过 commandId 匹配并 complete Future</li>
 *   <li><b>状态追踪</b>：指令详情持久化到 Redis Hash，支持跨请求状态查询和重发</li>
 * </ul>
 * </p>
 * <p>
 * <b>设计决策</b>：Redis（持久化状态） + JVM CompletableFuture（同步等待）双存储
 * <ul>
 *   <li>Redis 存储指令详情用于跨实例共享、应用重启恢复、定时任务扫描</li>
 *   <li>CompletableFuture 用于低延迟的同步等待（不经过网络）</li>
 *   <li>Redis TTL = 5 分钟，确保异常情况下不会永久占用内存</li>
 * </ul>
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
public class CommandResponseManager {

    /** Redis Key 前缀：指令详情 */
    private static final String KEY_COMMAND_DETAIL = "command:detail:";
    /** Redis Key 前缀：按设备 SN 索引的待确认指令 */
    private static final String KEY_COMMAND_PENDING = "command:pending:";
    /** 指令详情 TTL（秒），超时自动清理 */
    private static final long COMMAND_DETAIL_TTL_SECONDS = 300;

    /** Redis Hash 字段名 */
    private static final String FIELD_SN = "sn";
    private static final String FIELD_COMMAND = "command";
    private static final String FIELD_PARAMS = "params";
    private static final String FIELD_ORDER_NO = "orderNo";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_PACKET_ID = "packetId";
    private static final String FIELD_CREATE_TIME = "createTime";
    private static final String FIELD_LAST_SEND_TIME = "lastSendTime";
    private static final String FIELD_RETRY_COUNT = "retryCount";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 本地内存：commandId → CompletableFuture，用于同步等待设备响应
     * <p>
     * 使用 ConcurrentHashMap 保证线程安全。
     * Key 在收到响应或 Future 超时后移除，避免内存泄漏。
     * </p>
     */
    private final Map<String, CompletableFuture<CommandResult>> pendingFutures = new ConcurrentHashMap<>();

    public CommandResponseManager(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== 指令注册 ====================

    /**
     * 注册指令并同步等待设备响应
     * <p>
     * 将指令详情写入 Redis，在本地注册 CompletableFuture，
     * 阻塞当前线程等待设备响应或超时。
     * 用于 ChargeServiceImpl.startCharge() 的混合模式启桩流程。
     * </p>
     *
     * @param commandId 指令唯一ID
     * @param sn        设备SN
     * @param command   指令类型（如 START_CHARGE）
     * @param params    指令参数
     * @param orderNo   关联订单号
     * @param userId    操作用户ID
     * @param packetId  MQTT 报文标识符
     * @param timeoutMs 同步等待超时（毫秒）
     * @return 指令执行结果，TIMEOUT 表示同步等待超时但异步补偿仍在进行
     */
    public CommandResult registerAndWait(String commandId, String sn, String command,
                                         Map<String, Object> params, String orderNo,
                                         Long userId, int packetId, long timeoutMs) {
        // 1. 写入 Redis 指令详情
        writeCommandDetail(commandId, sn, command, params, orderNo, userId,
                packetId, CommandStatusEnum.SENT);

        // 2. 注册本地 CompletableFuture
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        pendingFutures.put(commandId, future);

        log.info("[指令管理] 注册指令并等待 - commandId: {}, SN: {}, 指令: {}, 超时: {}ms",
                commandId, sn, command, timeoutMs);

        // 3. 同步等待
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("[指令管理] 同步等待超时 - commandId: {}, SN: {}, 指令: {}，转入异步补偿",
                    commandId, sn, command);
            pendingFutures.remove(commandId);
            return CommandResult.TIMEOUT;
        } catch (Exception e) {
            log.error("[指令管理] 同步等待异常 - commandId: {}, SN: {}", commandId, sn, e);
            pendingFutures.remove(commandId);
            return CommandResult.TIMEOUT;
        }
    }

    /**
     * 仅注册指令到 Redis（异步模式，不阻塞等待）
     * <p>
     * 用于不需要同步等待响应的指令（如 STOP_CHARGE、RESTART），
     * 指令状态由 CommandTimeoutScheduler 异步追踪。
     * </p>
     *
     * @param commandId 指令唯一ID
     * @param sn        设备SN
     * @param command   指令类型
     * @param params    指令参数
     * @param orderNo   关联订单号
     * @param userId    操作用户ID
     * @param packetId  MQTT 报文标识符
     */
    public void registerAsync(String commandId, String sn, String command,
                              Map<String, Object> params, String orderNo,
                              Long userId, int packetId) {
        writeCommandDetail(commandId, sn, command, params, orderNo, userId,
                packetId, CommandStatusEnum.SENT);
        log.info("[指令管理] 注册指令（异步） - commandId: {}, SN: {}, 指令: {}", commandId, sn, command);
    }

    // ==================== 响应处理 ====================

    /**
     * 处理设备返回的指令响应
     * <p>
     * 由 MqttMessageHandler 在收到 device/command/response 时调用。
     * 解析 payload 中的 commandId 和 status，匹配对应指令并更新状态。
     * 如果该指令有正在等待的 CompletableFuture，则完成之。
     * </p>
     *
     * @param sn      设备SN
     * @param payload 响应消息体（JSON 格式）
     */
    public void handleResponse(String sn, String payload) {
        try {
            JSONObject json = JSONUtil.parseObj(payload);
            String commandId = json.getStr("commandId");
            String status = json.getStr("status");
            String message = json.getStr("message", "");

            if (commandId == null || commandId.isBlank()) {
                log.warn("[指令管理] 收到无 commandId 的响应 - SN: {}, payload: {}", sn, payload);
                return;
            }

            log.info("[指令管理] 收到指令响应 - commandId: {}, SN: {}, status: {}, message: {}",
                    commandId, sn, status, message);

            // 判断响应状态
            CommandResult result;
            CommandStatusEnum finalStatus;
            if ("SUCCESS".equalsIgnoreCase(status)) {
                result = CommandResult.SUCCESS;
                finalStatus = CommandStatusEnum.SUCCESS;
            } else {
                result = CommandResult.DEVICE_ERROR;
                finalStatus = CommandStatusEnum.DEVICE_ERROR;
            }

            // 更新 Redis 中的指令状态
            String detailKey = KEY_COMMAND_DETAIL + commandId;
            redisTemplate.opsForHash().put(detailKey, FIELD_STATUS, String.valueOf(finalStatus.getCode()));

            // 清除待确认索引
            removePendingIndex(sn, commandId);

            // 完成本地 Future（如果有）
            CompletableFuture<CommandResult> future = pendingFutures.remove(commandId);
            if (future != null) {
                future.complete(result);
                log.info("[指令管理] 已完成同步等待 - commandId: {}, result: {}", commandId, result);
            }

        } catch (Exception e) {
            log.error("[指令管理] 处理指令响应异常 - SN: {}, payload: {}", sn, payload, e);
        }
    }

    // ==================== PUBACK 送达确认 ====================

    /**
     * 更新指令为已送达状态（收到设备 PUBACK 时调用）
     * <p>
     * MQTT QoS 1 协议层：设备收到 PUBLISH 后回复 PUBACK，
     * 服务端收到 PUBACK 后调用此方法将指令状态从 SENT 更新为 ACKED。
     * </p>
     *
     * @param sn       设备SN
     * @param packetId MQTT 报文标识符
     */
    public void markAcked(String sn, int packetId) {
        // 按 packetId 匹配指令（同一个 SN 下 packetId 唯一）
        String detailKey = findCommandByPacketId(sn, packetId);
        if (detailKey == null) {
            log.debug("[指令管理] 未找到匹配 packetId 的指令 - SN: {}, packetId: {}", sn, packetId);
            return;
        }

        redisTemplate.opsForHash().put(detailKey, FIELD_STATUS,
                String.valueOf(CommandStatusEnum.ACKED.getCode()));
        log.debug("[指令管理] 指令已送达 - SN: {}, packetId: {}", sn, packetId);
    }

    // ==================== 定时任务查询接口 ====================

    /**
     * 扫描所有待确认的指令
     * <p>
     * 使用 Redis SCAN 遍历所有 command:detail:* Key，
     * 筛选状态为 SENT 或 ACKED（非终态）的指令。
     * 供 CommandTimeoutScheduler 使用。
     * </p>
     *
     * @return 待确认指令的 commandId 列表
     */
    public List<String> scanPendingCommands() {
        List<String> pendingIds = new ArrayList<>();

        try {
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(KEY_COMMAND_DETAIL + "*")
                    .count(50)
                    .build();
            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    try {
                        Object statusObj = redisTemplate.opsForHash().get(key, FIELD_STATUS);
                        if (statusObj != null) {
                            int statusCode = Integer.parseInt(statusObj.toString());
                            CommandStatusEnum status = CommandStatusEnum.fromCode(statusCode);
                            if (!status.isFinal()) {
                                // 提取 commandId（key 格式: command:detail:{commandId}）
                                String commandId = key.substring(KEY_COMMAND_DETAIL.length());
                                pendingIds.add(commandId);
                            }
                        }
                    } catch (Exception e) {
                        log.error("[指令管理] 扫描指令 key={} 时发生异常", key, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[指令管理] 扫描待确认指令异常", e);
        }

        log.debug("[指令管理] 扫描到 {} 条待确认指令", pendingIds.size());
        return pendingIds;
    }

    /**
     * 获取指令详情
     *
     * @param commandId 指令唯一ID
     * @return 指令详情 Map，如果不存在返回 null
     */
    public Map<Object, Object> getCommandDetail(String commandId) {
        String key = KEY_COMMAND_DETAIL + commandId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        return (entries != null && !entries.isEmpty()) ? entries : null;
    }

    /**
     * 更新指令重试信息
     *
     * @param commandId  指令唯一ID
     * @param retryCount 当前重试次数
     * @param newPacketId 新的 MQTT packetId（重发时分配）
     */
    public void updateRetryInfo(String commandId, int retryCount, int newPacketId) {
        String key = KEY_COMMAND_DETAIL + commandId;
        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_RETRY_COUNT, String.valueOf(retryCount));
        updates.put(FIELD_LAST_SEND_TIME, String.valueOf(System.currentTimeMillis()));
        updates.put(FIELD_PACKET_ID, String.valueOf(newPacketId));
        updates.put(FIELD_STATUS, String.valueOf(CommandStatusEnum.SENT.getCode()));
        redisTemplate.opsForHash().putAll(key, updates);
        log.info("[指令管理] 更新重试信息 - commandId: {}, retryCount: {}, newPacketId: {}",
                commandId, retryCount, newPacketId);
    }

    /**
     * 标记指令为终态并清理
     * <p>
     * 设置 Redis 中指令状态为终态，移除本地 Future，清除待确认索引。
     * Redis Key 依赖 TTL 自动过期，不立即删除以确保可追溯。
     * </p>
     *
     * @param commandId   指令唯一ID
     * @param finalStatus 终态状态（SUCCESS/DEVICE_ERROR/TIMEOUT）
     */
    public void markCompleted(String commandId, CommandStatusEnum finalStatus) {
        String key = KEY_COMMAND_DETAIL + commandId;
        redisTemplate.opsForHash().put(key, FIELD_STATUS, String.valueOf(finalStatus.getCode()));

        // 清除待确认索引
        Object snObj = redisTemplate.opsForHash().get(key, FIELD_SN);
        if (snObj != null) {
            removePendingIndex(snObj.toString(), commandId);
        }

        // 清除本地 Future
        CompletableFuture<CommandResult> future = pendingFutures.remove(commandId);
        if (future != null && !future.isDone()) {
            future.complete(CommandResult.TIMEOUT);
        }

        log.info("[指令管理] 指令已完成（终态） - commandId: {}, status: {}", commandId, finalStatus.getDesc());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将指令详情写入 Redis Hash
     *
     * @param commandId 指令唯一ID
     * @param sn        设备SN
     * @param command   指令类型
     * @param params    指令参数
     * @param orderNo   关联订单号
     * @param userId    操作用户ID
     * @param packetId  MQTT 报文标识符
     * @param status    初始状态
     */
    private void writeCommandDetail(String commandId, String sn, String command,
                                    Map<String, Object> params, String orderNo,
                                    Long userId, int packetId, CommandStatusEnum status) {
        String key = KEY_COMMAND_DETAIL + commandId;
        long now = System.currentTimeMillis();

        Map<String, Object> detail = new HashMap<>();
        detail.put(FIELD_SN, sn);
        detail.put(FIELD_COMMAND, command);
        detail.put(FIELD_PARAMS, params != null ? JSONUtil.toJsonStr(params) : "{}");
        detail.put(FIELD_ORDER_NO, orderNo != null ? orderNo : "");
        detail.put(FIELD_USER_ID, userId != null ? String.valueOf(userId) : "");
        detail.put(FIELD_STATUS, String.valueOf(status.getCode()));
        detail.put(FIELD_PACKET_ID, String.valueOf(packetId));
        detail.put(FIELD_CREATE_TIME, String.valueOf(now));
        detail.put(FIELD_LAST_SEND_TIME, String.valueOf(now));
        detail.put(FIELD_RETRY_COUNT, "0");

        redisTemplate.opsForHash().putAll(key, detail);
        redisTemplate.expire(key, COMMAND_DETAIL_TTL_SECONDS, TimeUnit.SECONDS);

        // 维护按 SN 的待确认指令索引
        addPendingIndex(sn, commandId);
    }

    /**
     * 将 commandId 添加到设备的待确认指令集合
     */
    private void addPendingIndex(String sn, String commandId) {
        String key = KEY_COMMAND_PENDING + sn;
        redisTemplate.opsForSet().add(key, commandId);
        redisTemplate.expire(key, COMMAND_DETAIL_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 从设备的待确认指令集合中移除 commandId
     */
    private void removePendingIndex(String sn, String commandId) {
        String key = KEY_COMMAND_PENDING + sn;
        redisTemplate.opsForSet().remove(key, commandId);
    }

    /**
     * 按 packetId 查找指令的 Redis Key
     * <p>
     * 通过设备的待确认索引缩小搜索范围，逐个检查指令的 packetId 字段。
     * </p>
     *
     * @param sn       设备SN
     * @param packetId MQTT 报文标识符
     * @return 匹配的 Redis Key，未找到返回 null
     */
    private String findCommandByPacketId(String sn, int packetId) {
        String pendingKey = KEY_COMMAND_PENDING + sn;
        Set<Object> commandIds = redisTemplate.opsForSet().members(pendingKey);
        if (commandIds == null || commandIds.isEmpty()) {
            return null;
        }

        String targetPacketId = String.valueOf(packetId);
        for (Object cid : commandIds) {
            String detailKey = KEY_COMMAND_DETAIL + cid;
            Object pid = redisTemplate.opsForHash().get(detailKey, FIELD_PACKET_ID);
            if (targetPacketId.equals(pid != null ? pid.toString() : null)) {
                return detailKey;
            }
        }
        return null;
    }
}
