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
import java.util.concurrent.Executors;
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
    /** 重连专用调度器（单线程，生命周期与设备一致） */
    private ScheduledExecutorService reconnectScheduler;
    /** 重试计数器（连接成功后归零） */
    private AtomicInteger retryCount;

    // ==================== 常量 ====================
    /** 最大重试间隔（秒） */
    private static final long MAX_BACKOFF_SECONDS = 60;
    private static final String TOPIC_HEARTBEAT = "device/heartbeat";
    private static final String TOPIC_STATUS = "device/status";
    private static final String TOPIC_DATA = "device/data";
    private static final String TOPIC_ALARM = "device/alarm";
    private static final String TOPIC_COMMAND_RESPONSE = "device/command/response";

    /**
     * 初始化并启动虚拟充电桩
     * <p>
     * 首次连接立即发起，连接成功后启动心跳/数据上报；
     * 连接失败则按指数退避策略无限重试，直到主动调用 stop()。
     * </p>
     */
    public void start() {
        this.running = true;
        this.retryCount = new AtomicInteger(0);
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("reconnect-" + sn).factory()
        );
        // 首次连接立即发起，后续由 attemptConnection 内部调度重试
        reconnectScheduler.execute(this::attemptConnection);
    }

    /**
     * 连接尝试（含退避重试）
     * <p>
     * 始终在 reconnectScheduler 单线程内执行，天然串行，无需额外同步。
     * 成功后调用 onConnected 启动心跳/数据上报；失败则按指数退避调度下一次重试。
     * </p>
     */
    private void attemptConnection() {
        if (!running) return;
        try {
            closeMqttClient();       // 清理上一次失败的客户端
            connectMqtt();           // 阻塞直到连接成功或超时
            onConnected();           // 启动心跳/数据上报
        } catch (Exception e) {
            if (!running) return;
            int count = retryCount.getAndIncrement();
            long delay = calculateBackoff(count);
            log.warn("[{}] 连接失败，{}秒后进行第{}次重试: {}",
                    sn, delay, count + 1, e.getMessage());
            reconnectScheduler.schedule(this::attemptConnection, delay, TimeUnit.SECONDS);
        }
    }

    /**
     * 指数退避：2s → 4s → 8s → 16s → 32s → 60s → 60s ...
     */
    private long calculateBackoff(int retryCount) {
        long delay = (long) Math.pow(2, retryCount + 1); // 2^1=2, 2^2=4, 2^3=8, ...
        return Math.min(delay, MAX_BACKOFF_SECONDS);
    }

    /**
     * 连接成功后初始化业务调度器
     */
    private void onConnected() {
        retryCount.set(0);  // 重试计数归零
        this.scheduler = new ScheduledThreadPoolExecutor(2,
                Thread.ofVirtual().name("charger-" + sn + "-", 0).factory());
        reportStatus(DeviceStatusEnum.IDLE);
        log.info("[{}] 虚拟充电桩上线成功，状态: {}", sn,
                status != null ? status.getDesc() : "空闲");
        heartbeatFuture = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat, heartbeatInterval, heartbeatInterval, TimeUnit.SECONDS);
    }

    /**
     * 连接断开后的清理与重连触发
     * <p>
     * 由 ChargeCommandCallback.connectionLost 调用（Paho 回调线程），
     * 清理旧调度器后，将重连任务提交到 reconnectScheduler。
     * </p>
     */
    public void onDisconnected() {
        if (!running) return;
        // 清理心跳/数据上报定时任务
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
            heartbeatFuture = null;
        }
        if (dataReportFuture != null) {
            dataReportFuture.cancel(true);
            dataReportFuture = null;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        // 清理旧 MQTT 客户端
        closeMqttClient();
        // 触发重连（提交到重连调度器线程）
        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            retryCount.set(0);
            reconnectScheduler.execute(this::attemptConnection);
        }
    }

    /**
     * 安全关闭 MQTT 客户端
     */
    private void closeMqttClient() {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                }
                mqttClient.close();
            } catch (MqttException ignored) {
                // 关闭旧客户端忽略异常
            }
            mqttClient = null;
        }
    }

    /**
     * 停止虚拟充电桩
     */
    public void stop() {
        this.running = false;
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
            heartbeatFuture = null;
        }
        if (dataReportFuture != null) {
            dataReportFuture.cancel(true);
            dataReportFuture = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            reconnectScheduler = null;
        }
        closeMqttClient();
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
        options.setAutomaticReconnect(false); // 由业务层统一管理重连策略

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
     * 回复指令确认（v2 协议，携带 commandId 用于去重和服务端匹配）
     * <p>
     * 响应格式：
     * <pre>
     * {
     *   "commandId": "雪花ID",       // 对应下发指令的 commandId
     *   "status": "SUCCESS",         // SUCCESS 或 ERROR
     *   "errorCode": "",             // 错误码（status=ERROR 时）
     *   "message": "充电已启动"       // 描述信息
     * }
     * </pre>
     * </p>
     *
     * @param commandId 指令唯一ID（对应服务端下发的 commandId）
     * @param command   指令类型（用于日志）
     * @param success   是否执行成功
     * @param message   结果描述
     */
    public void sendCommandResponse(String commandId, String command, boolean success, String message) {
        try {
            Map<String, Object> resp = new HashMap<>();
            resp.put("commandId", commandId);
            resp.put("status", success ? "SUCCESS" : "ERROR");
            resp.put("errorCode", success ? "" : "EXEC_FAILED");
            resp.put("message", message);

            publish(TOPIC_COMMAND_RESPONSE, JSONUtil.toJsonStr(resp), 1);
            log.info("[{}] 指令响应已发送 - commandId: {}, command: {}, status: {}",
                    sn, commandId, command, success ? "SUCCESS" : "ERROR");
        } catch (Exception e) {
            log.warn("[{}] 指令响应发送失败: {}", sn, e.getMessage());
        }
    }

    /**
     * 回复指令确认（v1 兼容，自动从消息 payload 中提取 commandId 失败时回退）
     * <p>
     * 优先使用 {@link #sendCommandResponse(String, String, boolean, String)} 明确传递 commandId。
     * 此方法仅在无法获取 commandId 时作为回退使用。
     * </p>
     *
     * @param command 指令类型
     * @param success 是否执行成功
     * @deprecated 使用 {@link #sendCommandResponse(String, String, boolean, String)} 代替
     */
    @Deprecated
    public void sendCommandResponse(String command, boolean success) {
        sendCommandResponse("unknown", command, success, success ? "已执行" : "执行失败");
    }

    // ==================== 充电模拟 ====================

    /**
     * 开始模拟充电
     * <p>
     * 启动数据上报定时任务（每5秒），并逐步增加已充电量。
     * </p>
     */
    public void startCharging() {
        // 初始化充电数据（先于状态上报，保证状态上报携带的第一帧 data 是真实值）
        this.voltage = 220 + Math.random() * 20;        // 220-240V
        this.current = 10 + Math.random() * 20;          // 10-30A
        this.power = voltage * current / 1000.0;         // kW
        this.chargedEnergy = 0;
        this.temperature = 30 + Math.random() * 10;      // 30-40℃

        reportStatus(DeviceStatusEnum.CHARGING);

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
