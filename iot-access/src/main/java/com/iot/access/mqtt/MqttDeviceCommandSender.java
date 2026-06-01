package com.iot.access.mqtt;

import cn.hutool.json.JSONUtil;
import com.iot.access.mqtt.codec.MqttMessage;
import com.iot.core.service.DeviceCommandSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * MQTT 设备指令下发实现
 * <p>
 * 实现 {@link DeviceCommandSender} 接口，通过 MQTT 协议向设备下发远程控制指令。
 * 指令发布到 topic: device/command/{sn}，QoS 1（至少一次送达）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttDeviceCommandSender implements DeviceCommandSender {

    /** 指令下发 Topic 前缀 */
    private static final String COMMAND_TOPIC_PREFIX = "device/command/";

    private final MqttSessionManager sessionManager;

    /**
     * 向指定设备下发指令
     * <p>
     * 将指令封装为 JSON 格式，通过 MQTT PUBLISH 发送到 device/command/{sn} 主题。
     * 使用 QoS 1 确保指令至少送达一次。
     * </p>
     *
     * @param sn      设备唯一序列号
     * @param command 指令类型
     * @param params  指令参数
     * @return true 消息已提交发送，false 设备不在线
     */
    @Override
    public boolean sendCommand(String sn, String command, Map<String, Object> params) {
        if (!sessionManager.isOnline(sn)) {
            log.warn("[指令下发] 设备不在线 - SN: {}", sn);
            return false;
        }

        // 构建指令 JSON 消息
        Map<String, Object> commandMsg = new java.util.HashMap<>();
        commandMsg.put("command", command);
        commandMsg.put("params", params);
        commandMsg.put("timestamp", System.currentTimeMillis());

        String topic = COMMAND_TOPIC_PREFIX + sn;
        byte[] payload = JSONUtil.toJsonStr(commandMsg).getBytes(StandardCharsets.UTF_8);

        // QoS 1 发送指令
        MqttMessage message = MqttMessage.publish(topic, payload, 1);
        boolean success = sessionManager.sendMessage(sn, message);

        log.info("[指令下发] SN: {}, 指令: {}, 结果: {}", sn, command, success ? "成功" : "失败");
        return success;
    }
}
