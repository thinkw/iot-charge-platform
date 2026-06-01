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
        log.warn("[{}] MQTT 连接断开: {}", charger.getSn(), cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.info("[{}] 收到指令 - topic: {}, payload: {}", charger.getSn(), topic, payload);

        try {
            JSONObject json = JSONUtil.parseObj(payload);
            String command = json.getStr("command");

            switch (command) {
                case "START_CHARGE" -> {
                    charger.startCharging();
                    charger.sendCommandResponse(command, true);
                }
                case "STOP_CHARGE" -> {
                    charger.stopCharging();
                    charger.sendCommandResponse(command, true);
                }
                case "RESTART" -> {
                    log.info("[{}] 收到重启指令，模拟设备重启", charger.getSn());
                    charger.sendCommandResponse(command, true);
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
                    charger.sendCommandResponse(command, false);
                }
            }
        } catch (Exception e) {
            log.error("[{}] 指令处理异常: {}", charger.getSn(), e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // QoS 1/2 消息送达确认，不需要额外处理
    }
}
