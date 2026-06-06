package com.iot.simulator.device;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * 充电桩指令回调处理器
 * <p>
 * 处理平台通过 MQTT 下发的远程控制指令，包括：
 * - START_CHARGE：启动充电
 * - STOP_CHARGE：停止充电
 * - RESTART：重启设备
 * - SET_PARAM：设置设备参数
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RequiredArgsConstructor
public class ChargeCommandCallback implements MqttCallback {

    private final VirtualCharger charger;

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[{}] MQTT 连接断开: {}", charger.getSn(),
                cause != null ? cause.getMessage() : "未知原因");
        charger.onDisconnected();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.info("[{}] 收到指令 - topic: {}, payload: {}", charger.getSn(), topic, payload);

        try {
            JSONObject json = JSONUtil.parseObj(payload);
            String command = json.getStr("command");
            // 提取 commandId 用于响应匹配和去重（v2 协议新增字段）
            String commandId = json.getStr("commandId", "unknown");

            // 检查是否为重复指令（简单去重：记录最近执行的 commandId）
            if (isDuplicateCommand(commandId)) {
                log.info("[{}] 重复指令，跳过执行但回复成功 - commandId: {}, command: {}",
                        charger.getSn(), commandId, command);
                charger.sendCommandResponse(commandId, command, true, "重复指令，已跳过");
                return;
            }

            switch (command) {
                case "START_CHARGE" -> {
                    charger.startCharging();
                    charger.sendCommandResponse(commandId, command, true, "充电已启动");
                }
                case "STOP_CHARGE" -> {
                    charger.stopCharging();
                    charger.sendCommandResponse(commandId, command, true, "充电已停止");
                }
                case "RESTART" -> {
                    log.info("[{}] 收到重启指令，模拟设备重启", charger.getSn());
                    charger.sendCommandResponse(commandId, command, true, "设备正在重启");
                    // 模拟短暂离线后重新上线
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            charger.reportStatus(com.iot.common.enums.DeviceStatusEnum.IDLE);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                }
                default -> {
                    log.warn("[{}] 未知指令: {}", charger.getSn(), command);
                    charger.sendCommandResponse(commandId, command, false, "未知指令: " + command);
                }
            }
        } catch (Exception e) {
            log.error("[{}] 指令处理异常: {}", charger.getSn(), e.getMessage());
        }
    }

    /**
     * 简单指令去重：检查 commandId 是否已在最近执行列表中
     * <p>
     * 使用固定大小的 LRU 集合（最多保留 100 条），防止内存无限增长。
     * 生产环境应由设备固件侧实现更可靠的持久化去重（如 Flash 存储）。
     * </p>
     *
     * @param commandId 指令唯一ID
     * @return true 如果指令已执行过
     */
    private boolean isDuplicateCommand(String commandId) {
        if ("unknown".equals(commandId)) {
            return false; // 无法判断，不拦截
        }
        // 使用 LinkedHashSet 作为简单 LRU 缓存
        if (executedCommands.contains(commandId)) {
            return true;
        }
        // 超过容量时移除最旧的条目
        if (executedCommands.size() >= MAX_COMMAND_HISTORY) {
            var iterator = executedCommands.iterator();
            iterator.next();
            iterator.remove();
        }
        executedCommands.add(commandId);
        return false;
    }

    /** 最近执行的指令 ID 集合（LRU 去重） */
    private final java.util.LinkedHashSet<String> executedCommands = new java.util.LinkedHashSet<>();
    /** 最多保留的指令历史条数 */
    private static final int MAX_COMMAND_HISTORY = 100;

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // QoS 1/2 消息送达确认，不需要额外处理
    }
}
