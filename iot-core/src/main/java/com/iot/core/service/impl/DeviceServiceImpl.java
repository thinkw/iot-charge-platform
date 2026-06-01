package com.iot.core.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.common.constant.DeviceConstants;
import com.iot.common.enums.AlarmLevelEnum;
import com.iot.common.enums.DeviceStatusEnum;
import com.iot.common.exception.BusinessException;
import com.iot.core.entity.Alarm;
import com.iot.core.entity.Charger;
import com.iot.core.entity.DeviceLog;
import com.iot.core.mapper.AlarmMapper;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mapper.DeviceLogMapper;
import com.iot.core.service.DeviceCommandSender;
import com.iot.core.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 设备管理服务实现类
 * <p>
 * 提供设备全生命周期管理的核心业务逻辑实现，包括：
 * - 设备鉴权（SN + 密钥验证）
 * - 设备上下线管理（Redis状态 + MySQL持久化 + MQ事件 + 操作日志）
 * - 心跳维护与超时检测
 * - 状态上报与状态机校验
 * - 实时数据更新
 * - 故障告警处理
 * - 远程指令下发
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    // ==================== Redis Key 常量 ====================
    /** 设备在线状态 Key 前缀，格式：device:status:{sn} */
    private static final String REDIS_KEY_DEVICE_STATUS = "device:status:";
    /** 设备实时数据 Key 前缀，格式：device:data:{sn} */
    private static final String REDIS_KEY_DEVICE_DATA = "device:data:";
    /** Redis Hash 中在线标识字段 */
    private static final String FIELD_ONLINE = "online";
    /** Redis Hash 中状态字段 */
    private static final String FIELD_STATUS = "status";
    /** Redis Hash 中最后心跳时间字段 */
    private static final String FIELD_LAST_HEARTBEAT = "lastHeartbeat";
    /** Redis Hash 中电压字段 */
    private static final String FIELD_VOLTAGE = "voltage";
    /** Redis Hash 中电流字段 */
    private static final String FIELD_CURRENT = "current";
    /** Redis Hash 中功率字段 */
    private static final String FIELD_POWER = "power";
    /** Redis Hash 中已充电量字段 */
    private static final String FIELD_ENERGY = "energy";
    /** Redis Hash 中温度字段 */
    private static final String FIELD_TEMPERATURE = "temperature";

    // ==================== 依赖注入 ====================
    private final ChargerMapper chargerMapper;
    private final DeviceLogMapper deviceLogMapper;
    private final AlarmMapper alarmMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 设备指令下发器，由 iot-access 模块实现 MQTT 下发。
     * required = false：当 iot-access 未加载时（如单元测试），此依赖为空。
     */
    @Autowired(required = false)
    private DeviceCommandSender deviceCommandSender;

    // ==================== 设备鉴权 ====================

    /**
     * 验证设备凭证
     * <p>
     * 根据 SN 查询 charger 表，验证 secret 是否匹配。
     * 当前使用简单的字符串比较，后续可升级为 HMAC-SHA256 签名验证。
     * </p>
     */
    @Override
    public boolean authenticateDevice(String sn, String secret) {
        if (sn == null || sn.isBlank() || secret == null || secret.isBlank()) {
            log.warn("[设备鉴权] SN或密钥为空");
            return false;
        }

        Charger charger = chargerMapper.selectOne(
                new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
        );

        if (charger == null) {
            log.warn("[设备鉴权] 设备不存在 - SN: {}", sn);
            return false;
        }

        // TODO: 使用 HMAC-SHA256 签名验证替代简单字符串比较
        // 当前使用设备SN后6位作为密钥（简化实现，方便模拟器测试）
        String expectedSecret = generateDeviceSecret(sn);
        boolean authenticated = expectedSecret.equals(secret);

        if (!authenticated) {
            log.warn("[设备鉴权] 密钥验证失败 - SN: {}", sn);
            return false;
        }

        log.info("[设备鉴权] 验证成功 - SN: {}", sn);
        return true;
    }

    // ==================== 设备上下线 ====================

    /**
     * 处理设备上线
     * <p>
     * 1. 更新 Redis 设备在线状态（online=1, status=IDLE, lastHeartbeat=当前时间）
     * 2. 更新 MySQL charger 表状态为 IDLE、最后上线时间
     * 3. 记录设备日志
     * 4. 发送 RocketMQ 设备上线事件
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOnline(String sn) {
        log.info("[设备上线] SN: {}", sn);
        long now = System.currentTimeMillis();

        // 1. 更新 Redis 设备状态
        String statusKey = REDIS_KEY_DEVICE_STATUS + sn;
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put(FIELD_ONLINE, 1);
        statusMap.put(FIELD_STATUS, DeviceStatusEnum.IDLE.getCode());
        statusMap.put(FIELD_LAST_HEARTBEAT, String.valueOf(now));
        redisTemplate.opsForHash().putAll(statusKey, statusMap);

        // 2. 更新 MySQL charger 表
        Charger charger = chargerMapper.selectOne(
                new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
        );
        if (charger != null) {
            charger.setStatus(DeviceStatusEnum.IDLE.getCode());
            charger.setLastOnlineTime(LocalDateTime.now());
            chargerMapper.updateById(charger);
        }

        // 3. 记录设备日志
        saveDeviceLog(charger != null ? charger.getId() : null, sn, "ONLINE",
                "设备上线, 状态: 空闲");

        // 4. 发送 RocketMQ 事件（best-effort，失败不影响主流程）
        sendDeviceEvent("ONLINE", sn, charger != null ? charger.getId() : null,
                charger != null ? charger.getStationId() : null, null);
    }

    /**
     * 处理设备离线
     * <p>
     * 1. 更新 Redis 设备在线状态（online=0, status=OFFLINE）
     * 2. 更新 MySQL charger 表状态为 OFFLINE
     * 3. 记录设备日志
     * 4. 发送 RocketMQ 设备离线事件
     * 5. 如果设备之前处于充电中状态，则创建离线告警
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOffline(String sn) {
        log.info("[设备离线] SN: {}", sn);

        // 读取离线前的状态，用于判断是否需要告警
        String statusKey = REDIS_KEY_DEVICE_STATUS + sn;
        Object prevStatusObj = redisTemplate.opsForHash().get(statusKey, FIELD_STATUS);
        int prevStatus = prevStatusObj != null ? Integer.parseInt(prevStatusObj.toString()) : DeviceStatusEnum.OFFLINE.getCode();

        // 1. 更新 Redis 设备状态
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put(FIELD_ONLINE, 0);
        statusMap.put(FIELD_STATUS, DeviceStatusEnum.OFFLINE.getCode());
        redisTemplate.opsForHash().putAll(statusKey, statusMap);

        // 2. 更新 MySQL charger 表
        Charger charger = chargerMapper.selectOne(
                new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
        );
        if (charger != null) {
            charger.setStatus(DeviceStatusEnum.OFFLINE.getCode());
            chargerMapper.updateById(charger);
        }

        // 3. 记录设备日志
        saveDeviceLog(charger != null ? charger.getId() : null, sn, "OFFLINE",
                "设备离线, 之前状态: " + prevStatus);

        // 4. 发送 RocketMQ 事件
        sendDeviceEvent("OFFLINE", sn, charger != null ? charger.getId() : null,
                charger != null ? charger.getStationId() : null, null);

        // 5. 如果设备在充电中离线，创建紧急告警
        if (prevStatus == DeviceStatusEnum.CHARGING.getCode() && charger != null) {
            createAlarm(charger.getId(), charger.getStationId(),
                    "OFFLINE", AlarmLevelEnum.URGENT.getCode(),
                    "设备在充电中异常离线，可能造成充电中断，请立即处理！");
        }
    }

    // ==================== 心跳处理 ====================

    /**
     * 处理设备心跳
     * <p>
     * 更新 Redis 中设备的心跳时间戳，并确保 online 标记为 1。
     * 如果设备之前标记为离线，首次心跳时会触发热恢复逻辑。
     * </p>
     */
    @Override
    public void handleHeartbeat(String sn) {
        String statusKey = REDIS_KEY_DEVICE_STATUS + sn;
        long now = System.currentTimeMillis();

        // 检查设备之前是否离线（热恢复场景）
        Object onlineObj = redisTemplate.opsForHash().get(statusKey, FIELD_ONLINE);
        boolean wasOffline = onlineObj == null || "0".equals(onlineObj.toString());

        // 更新心跳时间和在线状态
        redisTemplate.opsForHash().put(statusKey, FIELD_LAST_HEARTBEAT, String.valueOf(now));
        redisTemplate.opsForHash().put(statusKey, FIELD_ONLINE, 1);

        if (wasOffline) {
            log.info("[心跳恢复] SN: {} 之前处于离线状态，自动触发上线恢复", sn);
            // 确保状态为 IDLE（如果之前不是充电中）
            Object statusObj = redisTemplate.opsForHash().get(statusKey, FIELD_STATUS);
            if (statusObj == null || String.valueOf(DeviceStatusEnum.OFFLINE.getCode()).equals(statusObj.toString())) {
                redisTemplate.opsForHash().put(statusKey, FIELD_STATUS, DeviceStatusEnum.IDLE.getCode());
            }
        }
    }

    // ==================== 状态上报 ====================

    /**
     * 处理设备状态上报
     * <p>
     * 先校验状态机转换是否合法，合法则更新 Redis 状态和 MySQL，
     * 记录状态变更日志，发送 MQ 事件。
     * 如果同时携带了实时数据，一并更新。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleStatusReport(String sn, int status, Map<String, Object> data) {
        log.info("[状态上报] SN: {}, 目标状态: {}", sn, status);

        // 1. 读取当前状态
        String statusKey = REDIS_KEY_DEVICE_STATUS + sn;
        Object currentStatusObj = redisTemplate.opsForHash().get(statusKey, FIELD_STATUS);
        int currentStatus = currentStatusObj != null ?
                Integer.parseInt(currentStatusObj.toString()) : DeviceStatusEnum.OFFLINE.getCode();

        // 2. 状态机校验
        if (!validateStatusTransition(currentStatus, status)) {
            String msg = String.format("非法的状态转换: %s(%d) → %s(%d)",
                    DeviceStatusEnum.fromCode(currentStatus).getDesc(), currentStatus,
                    DeviceStatusEnum.fromCode(status).getDesc(), status);
            log.warn("[状态上报] {}", msg);
            throw new BusinessException(409, "设备状态转换不合法: " + msg);
        }

        // 3. 更新 Redis 状态
        redisTemplate.opsForHash().put(statusKey, FIELD_STATUS, status);
        redisTemplate.opsForHash().put(statusKey, FIELD_ONLINE, 1);

        // 4. 如果携带实时数据，更新 Redis 数据
        if (data != null && !data.isEmpty()) {
            updateRedisDeviceData(sn, data);
        }

        // 5. 更新 MySQL charger 表
        Charger charger = chargerMapper.selectOne(
                new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
        );
        if (charger != null) {
            charger.setStatus(status);
            updateChargerData(charger, data);
            chargerMapper.updateById(charger);
        }

        // 6. 记录状态变更日志
        DeviceStatusEnum newStatus = DeviceStatusEnum.fromCode(status);
        saveDeviceLog(charger != null ? charger.getId() : null, sn, "STATUS_CHANGE",
                String.format("状态变更: %s → %s, 数据: %s",
                        DeviceStatusEnum.fromCode(currentStatus).getDesc(),
                        newStatus.getDesc(),
                        data != null ? JSONUtil.toJsonStr(data) : "无"));

        // 7. 发送 MQ 事件
        sendDeviceEvent("STATUS_CHANGE", sn,
                charger != null ? charger.getId() : null,
                charger != null ? charger.getStationId() : null, data);
    }

    // ==================== 数据上报 ====================

    /**
     * 处理设备实时数据上报
     * <p>
     * 更新 Redis 中设备的实时运行数据，同时更新 MySQL charger 表。
     * 此方法不会触发热状态变更。
     * </p>
     */
    @Override
    public void handleDataReport(String sn, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            log.debug("[数据上报] SN: {}, 数据为空，跳过", sn);
            return;
        }

        log.debug("[数据上报] SN: {}, 数据: {}", sn, data);

        // 更新 Redis 实时数据
        updateRedisDeviceData(sn, data);

        // 更新 MySQL
        Charger charger = chargerMapper.selectOne(
                new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
        );
        if (charger != null) {
            updateChargerData(charger, data);
            chargerMapper.updateById(charger);
        }
    }

    // ==================== 告警上报 ====================

    /**
     * 处理设备故障上报
     * <p>
     * 创建告警记录，更新设备状态为故障(FAULT)，
     * 发送 RocketMQ 告警事件。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAlarmReport(String sn, String alarmType, int alarmLevel, String content) {
        log.warn("[故障上报] SN: {}, 类型: {}, 级别: {}, 内容: {}", sn, alarmType, alarmLevel, content);

        // 1. 查询设备信息
        Charger charger = chargerMapper.selectOne(
                new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
        );
        if (charger == null) {
            log.error("[故障上报] 设备不存在 - SN: {}", sn);
            return;
        }

        // 2. 创建告警记录
        createAlarm(charger.getId(), charger.getStationId(), alarmType, alarmLevel, content);

        // 3. 更新设备状态为故障
        String statusKey = REDIS_KEY_DEVICE_STATUS + sn;
        redisTemplate.opsForHash().put(statusKey, FIELD_STATUS, DeviceStatusEnum.FAULT.getCode());
        charger.setStatus(DeviceStatusEnum.FAULT.getCode());
        chargerMapper.updateById(charger);

        // 4. 记录日志
        saveDeviceLog(charger.getId(), sn, "FAULT",
                String.format("故障上报 - 类型: %s, 级别: %d, 内容: %s", alarmType, alarmLevel, content));

        // 5. 发送告警 MQ 事件
        sendAlarmEvent(charger.getId(), charger.getStationId(), alarmType, alarmLevel, content);
    }

    // ==================== 指令下发 ====================

    /**
     * 向设备下发远程控制指令
     * <p>
     * 先检查设备是否在线，在线则通过 DeviceCommandSender（MQTT）下发指令。
     * </p>
     */
    @Override
    public boolean sendCommand(String sn, String command, Map<String, Object> params) {
        if (!isDeviceOnline(sn)) {
            log.warn("[指令下发] 设备不在线，无法下发指令 - SN: {}", sn);
            return false;
        }

        if (deviceCommandSender == null) {
            log.warn("[指令下发] DeviceCommandSender 未注入（iot-access 模块未加载），指令未发送 - SN: {}, 指令: {}", sn, command);
            return false;
        }

        log.info("[指令下发] SN: {}, 指令: {}, 参数: {}", sn, command, params);
        return deviceCommandSender.sendCommand(sn, command, params);
    }

    // ==================== 状态机校验 ====================

    /**
     * 验证设备状态转换是否合法
     * <p>
     * 状态机规则：
     * OFFLINE(0) → IDLE(1)
     * IDLE(1) → LOCKED(4), FAULT(3), OFFLINE(0)
     * CHARGING(2) → IDLE(1), FAULT(3), OFFLINE(0)
     * FAULT(3) → IDLE(1), OFFLINE(0)
     * LOCKED(4) → CHARGING(2), IDLE(1), FAULT(3)
     * </p>
     */
    @Override
    public boolean validateStatusTransition(int currentStatus, int targetStatus) {
        // 同状态无需转换，视为合法
        if (currentStatus == targetStatus) {
            return true;
        }

        return switch (currentStatus) {
            case 0 -> targetStatus == 1;  // OFFLINE → IDLE
            case 1 -> targetStatus == 4 || targetStatus == 3 || targetStatus == 0;  // IDLE → LOCKED/FAULT/OFFLINE
            case 2 -> targetStatus == 1 || targetStatus == 3 || targetStatus == 0;  // CHARGING → IDLE/FAULT/OFFLINE
            case 3 -> targetStatus == 1 || targetStatus == 0;  // FAULT → IDLE/OFFLINE
            case 4 -> targetStatus == 2 || targetStatus == 1 || targetStatus == 3;  // LOCKED → CHARGING/IDLE/FAULT
            default -> false;
        };
    }

    // ==================== 在线检测 ====================

    @Override
    public boolean isDeviceOnline(String sn) {
        String statusKey = REDIS_KEY_DEVICE_STATUS + sn;
        Object onlineObj = redisTemplate.opsForHash().get(statusKey, FIELD_ONLINE);
        return onlineObj != null && "1".equals(onlineObj.toString());
    }

    // ==================== 心跳超时检测 ====================

    /**
     * 定时检查设备心跳超时
     * <p>
     * 每 30 秒执行一次，扫描 Redis 中所有 device:status:* 设备的 lastHeartbeat，
     * 将超过 90 秒（{@link DeviceConstants#HEARTBEAT_TIMEOUT}）未收到心跳的设备标记为离线。
     * </p>
     */
    @Override
    @Scheduled(fixedRate = 30000)
    public void checkHeartbeatTimeout() {
        long now = System.currentTimeMillis();
        long timeoutMillis = DeviceConstants.HEARTBEAT_TIMEOUT * 1000L;

        try {
            // 使用 SCAN 命令遍历所有 device:status:* 的 key
            Set<String> keys = redisTemplate.keys(REDIS_KEY_DEVICE_STATUS + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }

            for (String key : keys) {
                try {
                    Object heartbeatObj = redisTemplate.opsForHash().get(key, FIELD_LAST_HEARTBEAT);
                    Object onlineObj = redisTemplate.opsForHash().get(key, FIELD_ONLINE);

                    if (heartbeatObj == null) {
                        continue;
                    }

                    long lastHeartbeat = Long.parseLong(heartbeatObj.toString());
                    boolean isOnline = onlineObj != null && "1".equals(onlineObj.toString());

                    // 设备在线但心跳超时 → 标记离线
                    if (isOnline && (now - lastHeartbeat) > timeoutMillis) {
                        // 从 key 中提取 SN（key格式: device:status:CHARGER-001）
                        String sn = key.substring(REDIS_KEY_DEVICE_STATUS.length());
                        log.warn("[心跳超时] SN: {}, 最后心跳: {}ms 前，标记离线",
                                sn, now - lastHeartbeat);
                        handleOffline(sn);
                    }
                } catch (Exception e) {
                    log.error("[心跳超时] 处理 key={} 时发生异常", key, e);
                }
            }
        } catch (Exception e) {
            log.error("[心跳超时] 心跳检测定时任务异常", e);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 更新 Redis 中设备的实时数据
     *
     * @param sn   设备SN
     * @param data 实时数据 Map
     */
    private void updateRedisDeviceData(String sn, Map<String, Object> data) {
        String dataKey = REDIS_KEY_DEVICE_DATA + sn;
        Map<String, Object> hashData = new HashMap<>();

        // 将 data 中的值转为 String 存储（Redis Hash 只能存字符串）
        data.forEach((k, v) -> {
            if (v != null) {
                hashData.put(k, v.toString());
            }
        });

        if (!hashData.isEmpty()) {
            redisTemplate.opsForHash().putAll(dataKey, hashData);
        }
    }

    /**
     * 更新 Charger 实体中的实时数据字段
     *
     * @param charger 充电桩实体
     * @param data    实时数据 Map
     */
    private void updateChargerData(Charger charger, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        if (data.containsKey(FIELD_VOLTAGE) && data.get(FIELD_VOLTAGE) != null) {
            charger.setCurrentVoltage(new BigDecimal(data.get(FIELD_VOLTAGE).toString()));
        }
        if (data.containsKey(FIELD_CURRENT) && data.get(FIELD_CURRENT) != null) {
            charger.setCurrentCurrent(new BigDecimal(data.get(FIELD_CURRENT).toString()));
        }
        if (data.containsKey(FIELD_POWER) && data.get(FIELD_POWER) != null) {
            charger.setCurrentPower(new BigDecimal(data.get(FIELD_POWER).toString()));
        }
        if (data.containsKey(FIELD_ENERGY) && data.get(FIELD_ENERGY) != null) {
            charger.setChargedEnergy(new BigDecimal(data.get(FIELD_ENERGY).toString()));
        }
        if (data.containsKey(FIELD_TEMPERATURE) && data.get(FIELD_TEMPERATURE) != null) {
            charger.setTemperature(new BigDecimal(data.get(FIELD_TEMPERATURE).toString()));
        }
    }

    /**
     * 保存设备日志
     *
     * @param chargerId 充电桩ID
     * @param sn        设备SN
     * @param eventType 事件类型
     * @param content   事件内容
     */
    private void saveDeviceLog(Long chargerId, String sn, String eventType, String content) {
        DeviceLog deviceLog = new DeviceLog();
        deviceLog.setChargerId(chargerId);
        deviceLog.setSn(sn);
        deviceLog.setEventType(eventType);
        deviceLog.setContent(content);
        deviceLog.setCreateTime(LocalDateTime.now());
        deviceLogMapper.insert(deviceLog);
    }

    /**
     * 创建告警记录
     *
     * @param chargerId  充电桩ID
     * @param stationId  充电站ID
     * @param alarmType  告警类型
     * @param alarmLevel 告警级别
     * @param content    告警内容
     */
    private void createAlarm(Long chargerId, Long stationId, String alarmType, int alarmLevel, String content) {
        Alarm alarm = new Alarm();
        alarm.setChargerId(chargerId);
        alarm.setStationId(stationId);
        alarm.setAlarmType(alarmType);
        alarm.setAlarmLevel(alarmLevel);
        alarm.setContent(content);
        alarm.setStatus(0); // 未处理
        alarmMapper.insert(alarm);
        log.info("[创建告警] chargerId: {}, 类型: {}, 级别: {}", chargerId, alarmType, alarmLevel);
    }

    /**
     * 生成设备的简易密钥
     * <p>
     * 当前简化实现：使用 SN 后 6 位作为密钥。
     * 生产环境应使用 HMAC-SHA256 或其他安全方式。
     * </p>
     *
     * @param sn 设备SN
     * @return 设备密钥
     */
    private String generateDeviceSecret(String sn) {
        // 简化密钥生成：SN后6位
        if (sn.length() <= 6) {
            return sn;
        }
        return sn.substring(sn.length() - 6);
    }

    /**
     * 发送设备事件到 RocketMQ（best-effort）
     * <p>
     * 消息发送失败不抛出异常，仅记录警告日志，保证主流程不受影响。
     * </p>
     *
     * @param eventType  事件类型
     * @param sn         设备SN
     * @param chargerId  充电桩ID
     * @param stationId  充电站ID
     * @param data       附加数据
     */
    private void sendDeviceEvent(String eventType, String sn, Long chargerId, Long stationId, Map<String, Object> data) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("sn", sn);
            event.put("chargerId", chargerId);
            event.put("stationId", stationId);
            event.put("data", data);
            event.put("timestamp", System.currentTimeMillis());
            rocketMQTemplate.convertAndSend("device_event", event);
        } catch (Exception e) {
            log.warn("[MQ事件] 发送设备事件失败 - eventType: {}, SN: {}, error: {}",
                    eventType, sn, e.getMessage());
        }
    }

    /**
     * 发送告警事件到 RocketMQ（best-effort）
     *
     * @param chargerId  充电桩ID
     * @param stationId  充电站ID
     * @param alarmType  告警类型
     * @param alarmLevel 告警级别
     * @param content    告警内容
     */
    private void sendAlarmEvent(Long chargerId, Long stationId, String alarmType, int alarmLevel, String content) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "ALARM_CREATED");
            event.put("chargerId", chargerId);
            event.put("stationId", stationId);
            event.put("alarmType", alarmType);
            event.put("alarmLevel", alarmLevel);
            event.put("content", content);
            event.put("timestamp", System.currentTimeMillis());
            rocketMQTemplate.convertAndSend("alarm_event", event);
        } catch (Exception e) {
            log.warn("[MQ事件] 发送告警事件失败 - chargerId: {}, error: {}", chargerId, e.getMessage());
        }
    }
}
