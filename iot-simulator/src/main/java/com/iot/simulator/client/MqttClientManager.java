package com.iot.simulator.client;

import com.iot.common.enums.DeviceStatusEnum;
import com.iot.simulator.device.VirtualCharger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚拟充电桩管理器
 * <p>
 * 负责批量创建、启动、停止、监控虚拟充电桩。
 * 管理所有 VirtualCharger 实例的生命周期。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
public class MqttClientManager {

    @Value("${mqtt.broker}")
    private String brokerUrl;

    /** 所有管理的虚拟充电桩，key=SN */
    private final Map<String, VirtualCharger> chargers = new ConcurrentHashMap<>();

    /**
     * 批量启动虚拟充电桩
     *
     * @param count 启动数量
     * @return 启动的充电桩列表
     */
    public List<VirtualCharger> startChargers(int count) {
        List<VirtualCharger> started = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            String sn = String.format("CHARGER-%03d", i);
            // 密钥：SN后6位（与平台端 DeviceServiceImpl.generateDeviceSecret 保持一致）
            String secret = sn.length() <= 6 ? sn : sn.substring(sn.length() - 6);

            VirtualCharger charger = VirtualCharger.builder()
                    .sn(sn)
                    .secret(secret)
                    .brokerUrl(brokerUrl)
                    .heartbeatInterval(30)
                    .dataReportInterval(5)
                    .status(DeviceStatusEnum.IDLE)
                    .voltage(0)
                    .current(0)
                    .power(0)
                    .chargedEnergy(0)
                    .temperature(25.0)
                    .build();

            try {
                charger.start();
                chargers.put(sn, charger);
                started.add(charger);
                Thread.sleep(200); // 错开连接，避免瞬间压力
            } catch (Exception e) {
                log.error("启动虚拟充电桩失败 - SN: {}", sn, e);
            }
        }

        log.info("批量启动完成: 成功 {}/{} 台", started.size(), count);
        return started;
    }

    /**
     * 停止所有充电桩
     */
    public void stopAll() {
        log.info("停止所有虚拟充电桩，共 {} 台", chargers.size());
        chargers.values().forEach(VirtualCharger::stop);
        chargers.clear();
        log.info("所有虚拟充电桩已停止");
    }

    /**
     * 停止指定充电桩
     *
     * @param sn 设备SN
     */
    public boolean stopCharger(String sn) {
        VirtualCharger charger = chargers.remove(sn);
        if (charger != null) {
            charger.stop();
            log.info("已停止充电桩: {}", sn);
            return true;
        }
        log.warn("未找到充电桩: {}", sn);
        return false;
    }

    /**
     * 查看所有充电桩状态
     *
     * @return 状态列表
     */
    public List<String> getStatus() {
        List<String> statusList = new ArrayList<>();
        statusList.add(String.format("在线充电桩总数: %d", chargers.size()));
        for (VirtualCharger c : chargers.values()) {
            statusList.add(c.toString());
        }
        return statusList;
    }

    /**
     * 触发指定充电桩的故障
     *
     * @param sn        设备SN
     * @param alarmType 故障类型
     */
    public boolean triggerFault(String sn, String alarmType) {
        VirtualCharger charger = chargers.get(sn);
        if (charger == null) {
            log.warn("未找到充电桩: {}", sn);
            return false;
        }

        String content = switch (alarmType.toUpperCase()) {
            case "TEMP", "OVER_TEMP" -> {
                charger.setTemperature(85.0);
                yield "设备温度超过安全阈值，当前温度85℃";
            }
            case "VOLT", "OVER_VOLT" -> {
                charger.setVoltage(280.0);
                yield "设备电压异常偏高，当前电压280V";
            }
            case "SHORT", "SHORT_CIRCUIT" -> {
                charger.setCurrent(100.0);
                yield "检测到短路异常，电流急剧升高";
            }
            default -> "未知故障类型: " + alarmType;
        };

        charger.reportAlarm(alarmType.toUpperCase(), 2, content);
        return true;
    }

    /**
     * 恢复指定充电桩
     *
     * @param sn 设备SN
     */
    public boolean recoverCharger(String sn) {
        VirtualCharger charger = chargers.get(sn);
        if (charger == null) {
            log.warn("未找到充电桩: {}", sn);
            return false;
        }
        charger.setTemperature(30.0);
        charger.setVoltage(220.0);
        charger.setCurrent(0);
        charger.setPower(0);
        charger.reportStatus(DeviceStatusEnum.IDLE);
        log.info("充电桩 {} 已恢复为正常状态", sn);
        return true;
    }
}
