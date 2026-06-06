package com.iot.access.mqtt.command;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.access.mqtt.MqttDeviceCommandSender;
import com.iot.common.enums.CommandStatusEnum;
import com.iot.common.enums.OrderStatusEnum;
import com.iot.core.entity.ChargeOrder;
import com.iot.core.mapper.ChargeOrderMapper;
import com.iot.core.service.ChargeEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 指令超时补偿定时器
 * <p>
 * 定时扫描 Redis 中所有待确认指令（状态为 SENT 或 ACKED），
 * 执行超时检测、重发和补偿处理：
 * <ul>
 *   <li><b>重发</b>：指令已发送超过 2 秒未收到响应（PUBACK 或 device/command/response），
 *       且重试次数未达上限 → 重新下发指令</li>
 *   <li><b>超时取消</b>：指令总等待时间超过 10 秒 → 取消关联订单，WebSocket 推送失败通知</li>
 * </ul>
 * </p>
 * <p>
 * <b>扫描策略</b>：每 5 秒执行一次，使用 Redis SCAN 避免阻塞。
 * 重试使用固定间隔（2 秒），不采用指数退避（IoT 设备通信延迟相对稳定）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
public class CommandTimeoutScheduler {

    /** 指令总超时时间（毫秒），超过此时间仍未收到设备响应则取消订单 */
    private static final long COMMAND_TOTAL_TIMEOUT_MS = 10_000;

    /** 重试间隔（毫秒），距上次发送超过此时间且未收到响应则重发 */
    private static final long RETRY_INTERVAL_MS = 2_000;

    /** 最大重试次数（不含首次发送） */
    private static final int MAX_RETRIES = 2;

    private final CommandResponseManager commandResponseManager;
    private final ChargeOrderMapper chargeOrderMapper;
    private final MqttDeviceCommandSender mqttDeviceCommandSender;

    /**
     * ChargeEventPublisher 由 iot-api 模块实现（WebSocket 推送）。
     * required = false：当 iot-api 未加载时（如单元测试），此依赖为 null。
     */
    @Autowired(required = false)
    private ChargeEventPublisher chargeEventPublisher;

    public CommandTimeoutScheduler(CommandResponseManager commandResponseManager,
                                   ChargeOrderMapper chargeOrderMapper,
                                   MqttDeviceCommandSender mqttDeviceCommandSender) {
        this.commandResponseManager = commandResponseManager;
        this.chargeOrderMapper = chargeOrderMapper;
        this.mqttDeviceCommandSender = mqttDeviceCommandSender;
    }

    /**
     * 定时扫描待确认指令，处理重发和超时补偿
     * <p>
     * 每 5 秒执行一次。对每条待确认指令判断：
     * 1. 是否超过总超时时间 → 取消订单
     * 2. 是否需要重发 → 重新下发指令
     * </p>
     */
    @Scheduled(fixedRate = 5_000)
    public void checkTimeoutCommands() {
        try {
            var pendingIds = commandResponseManager.scanPendingCommands();
            if (pendingIds.isEmpty()) {
                return;
            }

            log.debug("[指令补偿] 扫描到 {} 条待确认指令", pendingIds.size());
            long now = System.currentTimeMillis();

            for (String commandId : pendingIds) {
                try {
                    processPendingCommand(commandId, now);
                } catch (Exception e) {
                    log.error("[指令补偿] 处理指令异常 - commandId: {}", commandId, e);
                }
            }
        } catch (Exception e) {
            log.error("[指令补偿] 定时扫描异常", e);
        }
    }

    /**
     * 处理单条待确认指令
     *
     * @param commandId 指令唯一ID
     * @param now       当前时间戳（毫秒）
     */
    private void processPendingCommand(String commandId, long now) {
        Map<Object, Object> detail = commandResponseManager.getCommandDetail(commandId);
        if (detail == null || detail.isEmpty()) {
            return; // 指令详情已被清理
        }

        long createTime = parseLong(detail.get("createTime"), now);
        long lastSendTime = parseLong(detail.get("lastSendTime"), createTime);
        int retryCount = parseInt(detail.get("retryCount"), 0);
        int maxRetries = parseInt(detail.get("maxRetries"), MAX_RETRIES);
        String sn = str(detail.get("sn"));
        String command = str(detail.get("command"));
        String orderNo = str(detail.get("orderNo"));
        String userIdStr = str(detail.get("userId"));
        Long userId = userIdStr.isEmpty() ? null : Long.parseLong(userIdStr);

        // 1. 检查是否超过总超时时间
        long elapsed = now - createTime;
        if (elapsed > COMMAND_TOTAL_TIMEOUT_MS) {
            log.warn("[指令补偿] 指令总超时 - commandId: {}, SN: {}, 指令: {}, 已过 {}ms",
                    commandId, sn, command, elapsed);
            handleTimeout(commandId, sn, command, orderNo, userId);
            return;
        }

        // 2. 检查是否需要重发
        long timeSinceLastSend = now - lastSendTime;
        if (timeSinceLastSend > RETRY_INTERVAL_MS && retryCount < maxRetries) {
            int newRetryCount = retryCount + 1;
            log.info("[指令补偿] 重发指令 - commandId: {}, SN: {}, 指令: {}, 第{}次重试（共{}次）",
                    commandId, sn, command, newRetryCount, maxRetries);

            // 从 Redis 解析原始指令参数
            Map<String, Object> params = null;
            String paramsJson = str(detail.get("params"));
            if (paramsJson != null && !paramsJson.isEmpty() && !"{}".equals(paramsJson)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = JSONUtil.toBean(paramsJson, Map.class);
                    params = parsed;
                } catch (Exception e) {
                    log.warn("[指令补偿] 解析指令参数失败 - commandId: {}, paramsJson: {}", commandId, paramsJson);
                    params = new java.util.HashMap<>();
                }
            } else {
                params = new java.util.HashMap<>();
            }

            // 通过 MqttDeviceCommandSender 重新下发指令（保持相同 commandId）
            int newPacketId = mqttDeviceCommandSender.resendCommand(commandId, sn, command, params);
            if (newPacketId > 0) {
                // 重发成功：递增重试计数并更新 Redis（防止下次扫描再次重发）
                commandResponseManager.updateRetryInfo(commandId, newRetryCount, newPacketId);
            }
        }
    }

    /**
     * 指令超时后的补偿处理
     * <p>
     * 将指令标记为 TIMEOUT，取消关联订单，通过 WebSocket 通知用户。
     * </p>
     *
     * @param commandId 指令唯一ID
     * @param sn        设备SN
     * @param command   指令类型
     * @param orderNo   关联订单号
     * @param userId    操作用户ID
     */
    private void handleTimeout(String commandId, String sn, String command,
                               String orderNo, Long userId) {
        // 1. 标记指令为 TIMEOUT
        commandResponseManager.markCompleted(commandId, CommandStatusEnum.TIMEOUT);

        // 2. 针对 START_CHARGE 指令，取消关联订单
        if ("START_CHARGE".equalsIgnoreCase(command) && orderNo != null && !orderNo.isEmpty()) {
            cancelOrder(orderNo, userId);
        }

        // 3. WebSocket 推送超时通知
        if (chargeEventPublisher != null && userId != null && orderNo != null) {
            try {
                chargeEventPublisher.publishCommandStatus(userId, orderNo, "TIMEOUT",
                        "设备未响应，充电启动失败，订单已取消");
            } catch (Exception e) {
                log.warn("[指令补偿] WebSocket 推送超时通知失败 - orderNo: {}", orderNo, e);
            }
        }

        log.info("[指令补偿] 超时处理完成 - commandId: {}, SN: {}, 指令: {}, orderNo: {}",
                commandId, sn, command, orderNo);
    }

    /**
     * 取消超时的待确认订单
     * <p>
     * 仅取消状态为 PENDING_CONFIRM 的订单，避免误取消已确认的订单。
     * </p>
     *
     * @param orderNo 订单编号
     * @param userId  用户ID（用于日志）
     */
    private void cancelOrder(String orderNo, Long userId) {
        try {
            ChargeOrder order = chargeOrderMapper.selectOne(
                    new LambdaQueryWrapper<ChargeOrder>().eq(ChargeOrder::getOrderNo, orderNo)
            );

            if (order == null) {
                log.warn("[指令补偿] 订单不存在，无法取消 - orderNo: {}", orderNo);
                return;
            }

            // 仅取消待确认状态的订单（防并发：可能已被对账确认为 CHARGING）
            if (order.getOrderStatus() != OrderStatusEnum.PENDING_CONFIRM.getCode()) {
                log.info("[指令补偿] 订单状态已变更，跳过取消 - orderNo: {}, status: {}",
                        orderNo, order.getOrderStatus());
                return;
            }

            order.setOrderStatus(OrderStatusEnum.CANCELLED.getCode());
            chargeOrderMapper.updateById(order);
            log.info("[指令补偿] 订单已取消 - orderNo: {}, userId: {}", orderNo, userId);

        } catch (Exception e) {
            log.error("[指令补偿] 取消订单异常 - orderNo: {}", orderNo, e);
            throw e;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 安全解析 Long，解析失败返回默认值
     */
    private long parseLong(Object value, long defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 安全解析 int，解析失败返回默认值
     */
    private int parseInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 安全转换 Object → String
     */
    private String str(Object value) {
        return value != null ? value.toString() : "";
    }
}
