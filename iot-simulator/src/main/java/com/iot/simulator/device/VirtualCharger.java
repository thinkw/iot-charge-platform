package com.iot.simulator.device;

import cn.hutool.json.JSONUtil;
import com.iot.common.enums.DeviceStatusEnum;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 虚拟充电桩实体
 * <p>
 * 模拟一个真实的充电桩设备，包含设备属性（SN、状态、实时数据）和行为
 * （心跳上报、状态上报、数据上报、故障触发、指令响应）。
 * 每个虚拟充电桩拥有独立的 MQTT 客户端连接和定时任务调度器。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Data
@Builder
public class VirtualCharger {

    // ==================== 静态 ====================
    /** 用于 packetId 递增 */
    private static final AtomicInteger PACKET_ID_GEN = new AtomicInteger(1);

    // ==================== 设备属性 ====================
    /** 设备唯一序列号 */
    private String sn;
    /** 设备密钥（SN后6位） */
    private String secret;
    /** 平台分配的充电桩ID */
    private Long chargerId;
    /** MQTT Broker URL */
    private String brokerUrl;
    /** 心跳间隔（秒） */
    private int heartbeatInterval;
    /** 数据上报间隔（秒） */
    private int dataReportInterval;

    // ==================== 运行状态 ====================
    /** 设备状态枚举 */
    private volatile DeviceStatusEnum status;
    /** 当前电压(V) */
    private volatile double voltage;
    /** 当前电流(A) */
    private volatile double current;
    /** 当前功率(kW) */
    private volatile double power;
    /** 已充电量(kWh) */
    private volatile double chargedEnergy;
    /** 设备温度(℃) */
    private volatile double temperature;
    /** 是否正在运行 */
    private volatile boolean running;

    // ==================== 内部组件 ====================
    /** MQTT 异步客户端 */
    private MqttAsyncClient mqttClient;
    /** 定时任务调度器（虚拟线程） */
    private ScheduledExecutorService scheduler;
    /** 心跳定时任务句柄 */
    private ScheduledFuture<?> heartbeatFuture;
    /** 数据上报定时任务句柄 */
    private ScheduledFuture<?> dataReportFuture;

    // ==================== 常量 ====================
    private static final String TOPIC_HEARTBEAT = "device/heartbeat";
    private static final String TOPIC_STATUS = "device/status";
    private static final String TOPIC_DATA = "device/data";
    private static final String TOPIC_ALARM = "device/alarm";
    private static final String TOPIC_COMMAND_RESPONSE = "device/command/response";

    /**
     * 初始化并启动虚拟充电桩
     * <p>
     * 1. 创建 MQTT 客户端并连接
     * 2. 上报设备上线状态
     * 3. 启动心跳定时任务
     * </p>
     */
    public void start() {
        try {
            connectMqtt();
            this.running = true;
            this.scheduler = new ScheduledThreadPoolExecutor(2, Thread.ofVirtual().name("charger-" + sn + "-", 0).factory());

            // 上报初始状态（空闲）
            reportStatus(DeviceStatusEnum.IDLE);
            log.info("[{}] 虚拟充电桩启动完成，状态: {}", sn, status != null ? status.getDesc() : "空闲");

            // 启动心跳定时任务
            heartbeatFuture = scheduler.scheduleAtFixedRate(
                    this::sendHeartbeat, 0, heartbeatInterval, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[{}] 虚拟充电桩启动失败", sn, e);
        }
    }

    /**
     * 停止虚拟充电桩
     */
    public void stop() {
        this.running = false;
        if (heartbeatFuture != null) heartbeatFuture.cancel(true);
        if (dataReportFuture != null) dataReportFuture.cancel(true);
        if (scheduler != null) scheduler.shutdownNow();
        disconnectMqtt();
        log.info("[{}] 虚拟充电桩已停止", sn);
    }

    // ==================== MQTT 连接 ====================

    /**
     * 建立 MQTT 连接
     */
    private void connectMqtt() throws MqttException {
        String clientId = sn + "-" + System.currentTimeMillis();
        // 使用内存持久化，避免在根目录生成 CHARGER-XXX-xxxxx-tcplocalhost1883 空文件夹
        mqttClient = new MqttAsyncClient(brokerUrl, clientId, new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(sn);          // username = SN
        options.setPassword(secret.toCharArray()); // password = secret
        options.setCleanSession(true);
        // 保持连接基于应用层 device/heartbeat（30s间隔 + 90s服务端超时检测），
        // 禁用 Paho MQTT 协议层 PINGREQ 以避免与应用层心跳冲突导致误断连
        options.setKeepAliveInterval(0);
        options.setConnectionTimeout(30);
        options.setAutomaticReconnect(true);  // 自动重连

        // 设置回调处理指令
        mqttClient.setCallback(new ChargeCommandCallback(this));

        IMqttToken token = mqttClient.connect(options);
        token.waitForCompletion(10000);

        if (!mqttClient.isConnected()) {
            throw new RuntimeException("MQTT 连接失败");
        }

        // 订阅指令下发主题
        String commandTopic = "device/command/" + sn;
        mqttClient.subscribe(commandTopic, 1);

        log.info("[{}] MQTT 连接成功，已订阅: {}", sn, commandTopic);
    }

    /**
     * 断开 MQTT 连接
     */
    private void disconnectMqtt() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
            } catch (MqttException e) {
                log.warn("[{}] MQTT 断开异常: {}", sn, e.getMessage());
            }
        }
    }

    // ==================== 模拟行为 ====================

    /**
     * 发送心跳
     * <p>
     * 向 device/heartbeat 主题发布心跳消息。
     * </p>
     */
    public void sendHeartbeat() {
        if (!running || mqttClient == null || !mqttClient.isConnected()) return;
        try {
            Map<String, Object> heartbeat = new HashMap<>();
            heartbeat.put("sn", sn);
            heartbeat.put("timestamp", System.currentTimeMillis());
            publish(TOPIC_HEARTBEAT, JSONUtil.toJsonStr(heartbeat), 0);
        } catch (Exception e) {
            log.warn("[{}] 心跳发送失败: {}", sn, e.getMessage());
        }
    }

    /**
     * 上报设备状态
     *
     * @param newStatus 新状态
     */
    public void reportStatus(DeviceStatusEnum newStatus) {
        if (!running) return;
        DeviceStatusEnum oldStatus = this.status;
        this.status = newStatus;

        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("sn", sn);
            msg.put("status", newStatus.getCode());
            msg.put("data", buildDataMap());

            publish(TOPIC_STATUS, JSONUtil.toJsonStr(msg), 1);
            log.info("[{}] 状态上报: {} → {}", sn, oldStatus != null ? oldStatus.getDesc() : "无", newStatus.getDesc());
        } catch (Exception e) {
            log.warn("[{}] 状态上报失败: {}", sn, e.getMessage());
        }
    }

    /**
     * 上报实时数据
     */
    public void reportData() {
        if (!running || mqttClient == null || !mqttClient.isConnected()) return;
        try {
            Map<String, Object> data = buildDataMap();
            publish(TOPIC_DATA, JSONUtil.toJsonStr(data), 0);
        } catch (Exception e) {
            log.warn("[{}] 数据上报失败: {}", sn, e.getMessage());
        }
    }

    /**
     * 上报故障
     *
     * @param alarmType  告警类型
     * @param alarmLevel 告警级别
     * @param content    告警内容
     */
    public void reportAlarm(String alarmType, int alarmLevel, String content) {
        if (!running) return;
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("sn", sn);
            msg.put("alarmType", alarmType);
            msg.put("alarmLevel", alarmLevel);
            msg.put("content", content);

            publish(TOPIC_ALARM, JSONUtil.toJsonStr(msg), 1);
            reportStatus(DeviceStatusEnum.FAULT);
            log.warn("[{}] 故障上报: {} - {}", sn, alarmType, content);
        } catch (Exception e) {
            log.warn("[{}] 故障上报失败: {}", sn, e.getMessage());
        }
    }

    /**
     * 回复指令确认
     */
    public void sendCommandResponse(String command, boolean success) {
        try {
            Map<String, Object> resp = new HashMap<>();
            resp.put("sn", sn);
            resp.put("command", command);
            resp.put("success", success);
            resp.put("timestamp", System.currentTimeMillis());

            publish(TOPIC_COMMAND_RESPONSE, JSONUtil.toJsonStr(resp), 1);
        } catch (Exception e) {
            log.warn("[{}] 指令响应发送失败: {}", sn, e.getMessage());
        }
    }

    // ==================== 充电模拟 ====================

    /**
     * 开始模拟充电
     * <p>
     * 启动数据上报定时任务（每5秒），并逐步增加已充电量。
     * </p>
     */
    public void startCharging() {
        reportStatus(DeviceStatusEnum.CHARGING);

        // 初始化充电数据
        this.voltage = 220 + Math.random() * 20;        // 220-240V
        this.current = 10 + Math.random() * 20;          // 10-30A
        this.power = voltage * current / 1000.0;         // kW
        this.chargedEnergy = 0;
        this.temperature = 30 + Math.random() * 10;      // 30-40℃

        // 每5秒上报一次实时数据
        dataReportFuture = scheduler.scheduleAtFixedRate(() -> {
            if (!running || status != DeviceStatusEnum.CHARGING) {
                if (dataReportFuture != null) dataReportFuture.cancel(false);
                return;
            }
            // 模拟充电进度
            this.chargedEnergy += this.power * (dataReportInterval / 3600.0); // kWh递增
            this.temperature += Math.random() * 0.5;          // 温度缓慢上升
            this.current = 10 + Math.random() * 20;           // 电流波动
            this.power = voltage * current / 1000.0;          // 功率波动
            reportData();
        }, 0, dataReportInterval, TimeUnit.SECONDS);

        log.info("[{}] 开始模拟充电 - 电压:{}V, 电流:{}A, 功率:{}kW", sn, voltage, current, power);
    }

    /**
     * 停止模拟充电
     */
    public void stopCharging() {
        if (dataReportFuture != null) {
            dataReportFuture.cancel(false);
            dataReportFuture = null;
        }
        this.current = 0;
        this.power = 0;
        reportStatus(DeviceStatusEnum.IDLE);
        log.info("[{}] 停止充电 - 累计电量:{}kWh", sn, chargedEnergy);
    }

    // ==================== 工具方法 ====================

    /**
     * 构建实时数据 Map
     */
    private Map<String, Object> buildDataMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("voltage", String.format("%.1f", voltage));
        data.put("current", String.format("%.1f", current));
        data.put("power", String.format("%.2f", power));
        data.put("energy", String.format("%.2f", chargedEnergy));
        data.put("temperature", String.format("%.1f", temperature));
        return data;
    }

    /**
     * MQTT 发布消息
     */
    private void publish(String topic, String payload, int qos) throws MqttException {
        MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        msg.setQos(qos);
        if (qos > 0) {
            msg.setId(PACKET_ID_GEN.getAndIncrement());
        }
        mqttClient.publish(topic, msg);
    }

    @Override
    public String toString() {
        return String.format("VirtualCharger{sn='%s', status=%s, voltage=%.1fV, current=%.1fA, power=%.2fkW, energy=%.2fkWh, temp=%.1f℃}",
                sn, status != null ? status.getDesc() : "未知", voltage, current, power, chargedEnergy, temperature);
    }
}
