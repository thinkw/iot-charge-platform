package com.iot.core.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.common.constant.DeviceConstants;
import com.iot.common.enums.AlarmLevelEnum;
import com.iot.common.enums.DeviceStatusEnum;
import com.iot.common.exception.BusinessException;
import com.iot.core.config.DeviceOfflineConfig;
import com.iot.core.entity.Alarm;
import com.iot.core.entity.Charger;
import com.iot.core.entity.DeviceLog;
import com.iot.common.enums.OrderStatusEnum;
import com.iot.core.entity.ChargeOrder;
import com.iot.core.mapper.AlarmMapper;
import com.iot.core.mapper.ChargeOrderMapper;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mapper.DeviceLogMapper;
import com.iot.common.model.CommandResult;
import com.iot.core.event.DeviceDataReportEvent;
import com.iot.core.service.ChargeEventPublisher;
import com.iot.core.service.DeviceCommandSender;
import com.iot.core.service.DeviceService;
import com.iot.core.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
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
import java.util.concurrent.TimeUnit;

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

    // Redis Key 和 Field 常量统一使用 DeviceConstants 中定义，不再重复声明

    // ==================== 依赖注入 ====================
    private final ChargerMapper chargerMapper;
    private final DeviceLogMapper deviceLogMapper;
    private final AlarmMapper alarmMapper;
    private final ChargeOrderMapper chargeOrderMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final DeviceOfflineConfig offlineConfig;

    /**
     * 订单服务，使用 @Lazy 打破与 OrderServiceImpl 之间的循环依赖。
     * OrderServiceImpl → DeviceService → OrderService（懒加载代理，首次调用时才解析）。
     */
    @Lazy
    @Autowired
    private OrderService orderService;

    /**
     * 设备指令下发器，由 iot-access 模块实现 MQTT 下发。
     * required = false：当 iot-access 未加载时（如单元测试），此依赖为空。
     */
    @Autowired(required = false)
    private DeviceCommandSender deviceCommandSender;

    /**
     * 充电事件推送器，由 iot-api 模块通过 WebSocket 实现。
     * required = false：当 iot-api 未加载时（如单元测试），此依赖为空。
     */
    @Autowired(required = false)
    private ChargeEventPublisher chargeEventPublisher;

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
            log.warn("[设备鉴权] SN或密钥为空 - SN: {}, secret: {}", sn, secret);
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
            log.warn("[设备鉴权] 密钥验证失败 - SN: {}, expected: '{}' (len={}), actual: '{}' (len={})",
                    sn, expectedSecret, expectedSecret.length(), secret, secret.length());
            return false;
        }

        log.info("[设备鉴权] 验证成功 - SN: {}", sn);
        return true;
    }

    // ==================== 设备上下线 ====================

    /**
     * 处理设备上线
     * <p>
     * 1. 检查是否有未结 CHARGING 订单 → 有则保持 CHARGING 状态，等待设备上报
     * 2. 更新 Redis 设备在线状态（online=1, status=IDLE 或 CHARGING）
     * 3. 更新 MySQL charger 表状态
     * 4. 清除离线时间标记和恢复等待标记
     * 5. 记录设备日志 + 发送 RocketMQ 设备上线事件
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOnline(String sn) {
        log.info("[设备上线] SN: {}", sn);
        long now = System.currentTimeMillis();

        // 1. 更新 Redis 设备状态
        String statusKey = DeviceConstants.REDIS_KEY_DEVICE_STATUS + sn;

        // 2. 查 charger
        Charger charger = chargerMapper.selectOne(
                new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
        );

        // 3. 判断是否有未结 CHARGING 订单（恢复对账）
        int targetStatus = DeviceStatusEnum.IDLE.getCode();
        String logStatus = "空闲";
        if (charger != null) {
            boolean hasChargingOrder = chargeOrderMapper.selectCount(
                    new LambdaQueryWrapper<ChargeOrder>()
                            .eq(ChargeOrder::getChargerId, charger.getId())
                            .eq(ChargeOrder::getOrderStatus, OrderStatusEnum.CHARGING.getCode())
            ) > 0;

            if (hasChargingOrder) {
                // 设备在充电中离线后恢复，保持 CHARGING 状态，等待设备上报实际状态
                targetStatus = DeviceStatusEnum.CHARGING.getCode();
                logStatus = "充电中(恢复)";

                // 记录恢复等待标记，60 秒宽限期内等待设备状态上报
                String recoveryKey = DeviceConstants.REDIS_KEY_RECOVERY_WAIT + sn;
                redisTemplate.opsForValue().set(recoveryKey,
                        String.valueOf(now + offlineConfig.getRecoveryGraceSeconds() * 1000L));
                // 设置 TTL：宽限期 + 60 秒冗余（异步任务扫描有延迟）
                redisTemplate.expire(recoveryKey,
                        offlineConfig.getRecoveryGraceSeconds() + 60, TimeUnit.SECONDS);

                log.info("[设备上线-恢复对账] SN: {}, 发现未结CHARGING订单, 设备状态保持CHARGING, "
                        + "宽限期={}秒, 等待设备上报实际状态",
                        sn, offlineConfig.getRecoveryGraceSeconds());
            }
        }

        // 4. 清除离线时间标记（设备已恢复）
        String offlineTimeKey = DeviceConstants.REDIS_KEY_OFFLINE_TIME + sn;
        redisTemplate.delete(offlineTimeKey);

        // 5. 更新 Redis
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put(DeviceConstants.FIELD_ONLINE, 1);
        statusMap.put(DeviceConstants.FIELD_STATUS, targetStatus);
        statusMap.put(DeviceConstants.FIELD_LAST_HEARTBEAT, String.valueOf(now));
        redisTemplate.opsForHash().putAll(statusKey, statusMap);

        // 6. 更新 MySQL charger 表
        if (charger != null) {
            charger.setStatus(targetStatus);
            charger.setLastOnlineTime(LocalDateTime.now());
            chargerMapper.updateById(charger);
        }

        // 7. 记录设备日志
        saveDeviceLog(charger != null ? charger.getId() : null, sn, "ONLINE",
                "设备上线, 状态: " + logStatus);

        // 8. 发送 RocketMQ 事件（best-effort，失败不影响主流程）
        sendDeviceEvent("ONLINE", sn, charger != null ? charger.getId() : null,
                charger != null ? charger.getStationId() : null, null);
    }

    /**
     * 处理设备离线
     * <p>
     * 只负责标记设备离线状态和创建告警，不直接终止订单。
     * 订单终止由 {@link #checkHeartbeatTimeout()} 统一处理，
     * 通过延迟确认机制避免网络抖动导致误终止。
     * </p>
     * <ol>
     *   <li>记录离线时间戳到 Redis（供超时判断用）</li>
     *   <li>更新 Redis 设备在线状态（online=0, status=OFFLINE）</li>
     *   <li>更新 MySQL charger 表状态为 OFFLINE</li>
     *   <li>记录设备日志</li>
     *   <li>发送 RocketMQ 设备离线事件</li>
     *   <li>如果设备之前处于充电中状态，则创建紧急告警</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOffline(String sn) {
        log.info("[设备离线] SN: {}", sn);

        // 读取离线前的状态，用于判断是否需要告警
        String statusKey = DeviceConstants.REDIS_KEY_DEVICE_STATUS + sn;
        Object prevStatusObj = redisTemplate.opsForHash().get(statusKey, DeviceConstants.FIELD_STATUS);
        int prevStatus = prevStatusObj != null ? Integer.parseInt(prevStatusObj.toString()) : DeviceStatusEnum.OFFLINE.getCode();

        // 0. 记录离线时间戳，供 checkHeartbeatTimeout 和 reconcileOrphanOrders 判断离线时长
        if (prevStatus == DeviceStatusEnum.CHARGING.getCode()) {
            String offlineTimeKey = DeviceConstants.REDIS_KEY_OFFLINE_TIME + sn;
            redisTemplate.opsForValue().set(offlineTimeKey, String.valueOf(System.currentTimeMillis()));
            log.info("[设备离线] SN: {} 在充电中离线，已记录离线时间戳，订单将在心跳超时后自动终止", sn);
        }

        // 1. 更新 Redis 设备状态
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put(DeviceConstants.FIELD_ONLINE, 0);
        statusMap.put(DeviceConstants.FIELD_STATUS, DeviceStatusEnum.OFFLINE.getCode());
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

        // 5. 如果设备在充电中离线，创建紧急告警（订单终止由 checkHeartbeatTimeout 异步处理）
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
     * 如果设备之前标记为离线，首次心跳时会触发热恢复逻辑：
     * <ul>
     *   <li>查 charger 是否有未结 CHARGING 订单 → 有则保持 CHARGING 状态</li>
     *   <li>无 CHARGING 订单 → 设为 IDLE</li>
     *   <li>清除离线时间标记</li>
     * </ul>
     * </p>
     */
    @Override
    public void handleHeartbeat(String sn) {
        String statusKey = DeviceConstants.REDIS_KEY_DEVICE_STATUS + sn;
        long now = System.currentTimeMillis();

        // 检查设备之前是否离线（热恢复场景）
        Object onlineObj = redisTemplate.opsForHash().get(statusKey, DeviceConstants.FIELD_ONLINE);
        boolean wasOffline = onlineObj == null || "0".equals(onlineObj.toString());

        // 更新心跳时间和在线状态
        redisTemplate.opsForHash().put(statusKey, DeviceConstants.FIELD_LAST_HEARTBEAT, String.valueOf(now));
        redisTemplate.opsForHash().put(statusKey, DeviceConstants.FIELD_ONLINE, 1);

        if (wasOffline) {
            log.info("[心跳恢复] SN: {} 之前处于离线状态，自动触发上线恢复", sn);

            // 清除离线时间标记
            String offlineTimeKey = DeviceConstants.REDIS_KEY_OFFLINE_TIME + sn;
            redisTemplate.delete(offlineTimeKey);

            // 判断是否需要恢复为 CHARGING 状态（对账未结订单）
            int targetStatus = DeviceStatusEnum.IDLE.getCode();
            try {
                Charger charger = chargerMapper.selectOne(
                        new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
                );
                if (charger != null) {
                    boolean hasChargingOrder = chargeOrderMapper.selectCount(
                            new LambdaQueryWrapper<ChargeOrder>()
                                    .eq(ChargeOrder::getChargerId, charger.getId())
                                    .eq(ChargeOrder::getOrderStatus, OrderStatusEnum.CHARGING.getCode())
                    ) > 0;

                    if (hasChargingOrder) {
                        targetStatus = DeviceStatusEnum.CHARGING.getCode();
                        // 记录恢复等待标记
                        String recoveryKey = DeviceConstants.REDIS_KEY_RECOVERY_WAIT + sn;
                        redisTemplate.opsForValue().set(recoveryKey,
                                String.valueOf(now + offlineConfig.getRecoveryGraceSeconds() * 1000L));
                        redisTemplate.expire(recoveryKey,
                                offlineConfig.getRecoveryGraceSeconds() + 60, TimeUnit.SECONDS);
                        log.info("[心跳恢复-对账] SN: {}, 发现未结CHARGING订单, 设备状态设为CHARGING, 宽限期={}秒",
                                sn, offlineConfig.getRecoveryGraceSeconds());
                    }
                }
            } catch (Exception e) {
                log.warn("[心跳恢复] SN: {} 查询CHARGING订单异常，默认设为IDLE", sn, e);
            }

            redisTemplate.opsForHash().put(statusKey, DeviceConstants.FIELD_STATUS, targetStatus);
            log.info("[心跳恢复] SN: {}, 状态设为 {}", sn,
                    DeviceStatusEnum.fromCode(targetStatus).getDesc());
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
        String statusKey = DeviceConstants.REDIS_KEY_DEVICE_STATUS + sn;
        Object currentStatusObj = redisTemplate.opsForHash().get(statusKey, DeviceConstants.FIELD_STATUS);
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

        // 2.5 查询恢复等待标记（设备重连宽限期），用于后续校验
        String recoveryKey = DeviceConstants.REDIS_KEY_RECOVERY_WAIT + sn;
        boolean inRecovery = Boolean.TRUE.equals(redisTemplate.hasKey(recoveryKey));

        // 2.6 恢复期状态校验：设备重连后如果在恢复等待期内，检查上报状态是否与预期一致
        //     若设备有未结 CHARGING 订单但上报 IDLE，说明设备端未恢复充电状态（如模拟器重启），
        //     拒绝 IDLE 上报并重发 START_CHARGE 指令
        if (status == DeviceStatusEnum.IDLE.getCode() && inRecovery) {
            // 恢复期内设备上报 IDLE → 检查是否存在未结 CHARGING 订单
            Charger chargerForRecovery = chargerMapper.selectOne(
                    new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
            );
            if (chargerForRecovery != null) {
                boolean hasChargingOrder = chargeOrderMapper.selectCount(
                        new LambdaQueryWrapper<ChargeOrder>()
                                .eq(ChargeOrder::getChargerId, chargerForRecovery.getId())
                                .eq(ChargeOrder::getOrderStatus, OrderStatusEnum.CHARGING.getCode())
                ) > 0;

                if (hasChargingOrder) {
                    // 设备有未结订单但上报 IDLE → 拒绝状态变更，重发 START_CHARGE
                    log.warn("[状态上报-恢复期拒绝] SN: {} 在恢复期内上报 IDLE，但存在 CHARGING 订单，"
                            + "拒绝状态变更并重发启动指令", sn);
                    // 保持当前 CHARGING 状态不变，不清除恢复标记（宽限期继续）
                    // 重发 START_CHARGE 指令
                    if (deviceCommandSender != null) {
                        try {
                            Map<String, Object> cmdParams = new HashMap<>();
                            cmdParams.put("recovery", true);
                            deviceCommandSender.sendCommand(sn, "START_CHARGE", cmdParams, null, null);
                        } catch (Exception e) {
                            log.warn("[状态上报-恢复期拒绝] 重发指令失败 - SN: {}, error: {}", sn, e.getMessage());
                        }
                    }
                    return; // 不更新状态，等待设备下次上报 CHARGING
                }
            }
        }

        // 3. 更新 Redis 状态
        redisTemplate.opsForHash().put(statusKey, DeviceConstants.FIELD_STATUS, status);
        redisTemplate.opsForHash().put(statusKey, DeviceConstants.FIELD_ONLINE, 1);

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

        // 7. 设备上报 CHARGING 状态 → 对账确认 AWAITING_DEVICE 订单
        if (status == DeviceStatusEnum.CHARGING.getCode() && charger != null) {
            reconcilePendingOrders(charger.getId(), sn);
        }

        // 7.5 设备主动上报状态后，清除恢复等待标记（设备已告知实际状态，宽限期结束）
        if (inRecovery) {
            redisTemplate.delete(recoveryKey);
            log.info("[状态上报] SN: {} 已上报状态, 清除恢复等待标记", sn);
        }

        // 8. 发送 MQ 事件
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

        // 发布 Spring 事件，供 ChargeService 监听并推送 WebSocket 充电进度
        if (charger != null) {
            try {
                eventPublisher.publishEvent(new DeviceDataReportEvent(sn, charger.getId(), data));
            } catch (Exception e) {
                log.warn("[数据上报] 发布 DeviceDataReportEvent 失败 - SN: {}, error: {}", sn, e.getMessage());
            }
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
        String statusKey = DeviceConstants.REDIS_KEY_DEVICE_STATUS + sn;
        redisTemplate.opsForHash().put(statusKey, DeviceConstants.FIELD_STATUS, DeviceStatusEnum.FAULT.getCode());
        charger.setStatus(DeviceStatusEnum.FAULT.getCode());
        chargerMapper.updateById(charger);

        // 4. 记录日志
        saveDeviceLog(charger.getId(), sn, "FAULT",
                String.format("故障上报 - 类型: %s, 级别: %d, 内容: %s", alarmType, alarmLevel, content));

        // 5. 发送告警 MQ 事件
        sendAlarmEvent(charger.getId(), charger.getStationId(), alarmType, alarmLevel, content);

        // 6. 自动终止该设备上 CHARGING 状态的充电订单
        //    故障期间设备无法继续充电，系统代为终止并推送 CHARGE_STOP(ABNORMAL) 通知用户
        try {
            terminateChargingOrdersForDevice(sn);
        } catch (Exception e) {
            log.error("[故障上报] 自动终止订单异常 - SN: {}, error: {}", sn, e.getMessage(), e);
        }
    }

    // ==================== 指令下发 ====================

    /**
     * 向设备下发远程控制指令（v1 兼容接口，不带响应追踪）
     * <p>
     * 先检查设备是否在线，在线则通过 DeviceCommandSender（MQTT）下发指令。
     * 内部委托给 v2 接口，自动生成 commandId 以保证链路一致性。
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

        // 委托给 v2 接口，不传业务关联信息
        String commandId = deviceCommandSender.sendCommand(sn, command, params, null, null);
        return commandId != null;
    }

    /**
     * 向设备下发远程控制指令（v2 带响应追踪和业务关联）
     * <p>
     * 先检查设备是否在线，在线则通过 DeviceCommandSender（MQTT）下发指令。
     * 携带订单号和用户ID用于超时补偿时的订单取消和 WebSocket 通知。
     * </p>
     *
     * @param sn      设备唯一序列号
     * @param command 指令类型
     * @param params  指令参数
     * @param orderNo 关联订单号（可选）
     * @param userId  操作用户ID（可选）
     * @return commandId 如果发送成功，null 如果失败
     */
    @Override
    public String sendCommand(String sn, String command, Map<String, Object> params,
                              String orderNo, Long userId) {
        if (!isDeviceOnline(sn)) {
            log.warn("[指令下发] 设备不在线，无法下发指令 - SN: {}", sn);
            return null;
        }

        if (deviceCommandSender == null) {
            log.warn("[指令下发] DeviceCommandSender 未注入（iot-access 模块未加载），指令未发送 - SN: {}, 指令: {}", sn, command);
            return null;
        }

        log.info("[指令下发] SN: {}, 指令: {}, commandId生成中..., orderNo: {}", sn, command, orderNo);
        return deviceCommandSender.sendCommand(sn, command, params, orderNo, userId);
    }

    /**
     * 向设备下发指令并同步等待响应（混合模式核心方法）
     * <p>
     * 先检查设备是否在线，在线则委托 DeviceCommandSender 发送并等待。
     * </p>
     */
    @Override
    public CommandResult sendCommandAndWait(String sn, String command, Map<String, Object> params,
                                            String orderNo, Long userId, long timeoutMs) {
        if (!isDeviceOnline(sn)) {
            log.warn("[指令下发-同步] 设备不在线 - SN: {}", sn);
            return null;
        }

        if (deviceCommandSender == null) {
            log.warn("[指令下发-同步] DeviceCommandSender 未注入");
            return null;
        }

        return deviceCommandSender.sendCommandAndWait(sn, command, params, orderNo, userId, timeoutMs);
    }

    // ==================== 状态机校验 ====================

    /**
     * 验证设备状态转换是否合法
     * <p>
     * 状态机规则：
     * OFFLINE(0) → IDLE(1), CHARGING(2)   ← 设备断电恢复时可能直接进入 CHARGING（断电前正在充电）
     * IDLE(1)   → CHARGING(2), LOCKED(4), FAULT(3), OFFLINE(0)
     *                                        ↑ 扫码启动充电：MQTT START_CHARGE → 设备回 status=2 是最常见路径
     * CHARGING(2) → IDLE(1), FAULT(3), OFFLINE(0)
     * FAULT(3)  → IDLE(1), OFFLINE(0)
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
            case 0 -> targetStatus == 1 || targetStatus == 2;  // OFFLINE → IDLE / CHARGING
            case 1 -> targetStatus == 2 || targetStatus == 4 || targetStatus == 3 || targetStatus == 0;  // IDLE → CHARGING / LOCKED / FAULT / OFFLINE
            case 2 -> targetStatus == 1 || targetStatus == 3 || targetStatus == 0;  // CHARGING → IDLE / FAULT / OFFLINE
            case 3 -> targetStatus == 1 || targetStatus == 0;  // FAULT → IDLE / OFFLINE
            case 4 -> targetStatus == 2 || targetStatus == 1 || targetStatus == 3;  // LOCKED → CHARGING / IDLE / FAULT
            default -> false;
        };
    }

    // ==================== 在线检测 ====================

    @Override
    public boolean isDeviceOnline(String sn) {
        String statusKey = DeviceConstants.REDIS_KEY_DEVICE_STATUS + sn;
        Object onlineObj = redisTemplate.opsForHash().get(statusKey, DeviceConstants.FIELD_ONLINE);
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
    /**
     * 定时检查设备心跳超时
     * <p>
     * 使用配置的扫描间隔（默认 30 秒），扫描 Redis 中所有设备的心跳时间，
     * 将超过心跳超时阈值（默认 90 秒）的设备标记为离线。
     * 同时，如果设备之前处于充电中状态，则自动终止关联的 CHARGING 订单。
     * <b>此方法是订单自动终止的唯一入口。</b>
     * </p>
     */
    @Override
    @Scheduled(fixedDelayString = "${device.offline.heartbeat-scan-interval-ms:30000}")
    public void checkHeartbeatTimeout() {
        long now = System.currentTimeMillis();
        long timeoutMillis = offlineConfig.getHeartbeatTimeoutSeconds() * 1000L;

        try {
            // 使用 SCAN 命令遍历所有 device:status:* 的 key（避免 KEYS 阻塞 Redis）
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(DeviceConstants.REDIS_KEY_DEVICE_STATUS + "*")
                    .count(100)
                    .build();
            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    try {
                        Object heartbeatObj = redisTemplate.opsForHash().get(key, DeviceConstants.FIELD_LAST_HEARTBEAT);
                        Object onlineObj = redisTemplate.opsForHash().get(key, DeviceConstants.FIELD_ONLINE);

                        if (heartbeatObj == null) {
                            continue;
                        }

                        long lastHeartbeat = Long.parseLong(heartbeatObj.toString());
                        boolean isOnline = onlineObj != null && "1".equals(onlineObj.toString());

                        // 设备在线但心跳超时 → 标记离线
                        if (isOnline && (now - lastHeartbeat) > timeoutMillis) {
                            // 从 key 中提取 SN（key格式: device:status:CHARGER-001）
                            String sn = key.substring(DeviceConstants.REDIS_KEY_DEVICE_STATUS.length());
                            long offlineDuration = now - lastHeartbeat;
                            log.warn("[心跳超时] SN: {}, 最后心跳: {}ms 前，触发离线处理及订单终止检查",
                                    sn, offlineDuration);
                            handleOffline(sn);

                            // 检查并终止该设备关联的 CHARGING 订单（唯一终止入口）
                            terminateChargingOrdersForDevice(sn);
                        }
                    } catch (Exception e) {
                        log.error("[心跳超时] 处理 key={} 时发生异常", key, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[心跳超时] 心跳检测定时任务异常", e);
        }
    }

    /**
     * 终止指定设备上所有 CHARGING 状态的订单
     * <p>
     * 查询该设备 SN 关联充电桩上所有 orderStatus=CHARGING 的订单，
     * 逐一调用 {@link OrderService#autoTerminateOrder(String, String)} 进行终止。
     * 每个订单内部已有分布式锁和双重检查保护，此处不做额外加锁。
     * </p>
     *
     * @param sn 设备唯一序列号
     */
    private void terminateChargingOrdersForDevice(String sn) {
        try {
            Charger charger = chargerMapper.selectOne(
                    new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
            );
            if (charger == null) {
                return;
            }

            var chargingOrders = chargeOrderMapper.selectList(
                    new LambdaQueryWrapper<ChargeOrder>()
                            .eq(ChargeOrder::getChargerId, charger.getId())
                            .eq(ChargeOrder::getOrderStatus, OrderStatusEnum.CHARGING.getCode())
            );

            if (chargingOrders == null || chargingOrders.isEmpty()) {
                return;
            }

            log.info("[心跳超时-订单终止] SN: {}, 发现 {} 个 CHARGING 订单待终止",
                    sn, chargingOrders.size());

            for (ChargeOrder order : chargingOrders) {
                try {
                    long offlineMillis = getOfflineDuration(sn);
                    String reason = "DEVICE_OFFLINE_TIMEOUT";
                    log.info("[心跳超时-订单终止] SN: {}, orderNo: {}, 离线时长={}ms, 触发自动终止",
                            sn, order.getOrderNo(), offlineMillis);
                    orderService.autoTerminateOrder(order.getOrderNo(), reason);
                } catch (Exception e) {
                    log.error("[心跳超时-订单终止] orderNo: {} 终止异常", order.getOrderNo(), e);
                }
            }
        } catch (Exception e) {
            log.error("[心跳超时-订单终止] SN: {} 查询CHARGING订单异常", sn, e);
        }
    }

    /**
     * 获取设备离线时长（毫秒）
     *
     * @param sn 设备序列号
     * @return 离线时长毫秒数，无记录时返回 0
     */
    private long getOfflineDuration(String sn) {
        try {
            String offlineTimeKey = DeviceConstants.REDIS_KEY_OFFLINE_TIME + sn;
            Object offlineTimeObj = redisTemplate.opsForValue().get(offlineTimeKey);
            if (offlineTimeObj != null) {
                long offlineTime = Long.parseLong(offlineTimeObj.toString());
                return System.currentTimeMillis() - offlineTime;
            }
        } catch (Exception e) {
            log.debug("[离线时长] SN: {} 获取离线时长失败", sn, e);
        }
        return 0;
    }

    /**
     * 兜底定时任务：扫描孤儿订单（设备离线但订单仍为 CHARGING）
     * <p>
     * 作为 {@link #checkHeartbeatTimeout()} 的兜底保障，使用配置的扫描间隔（默认 60 秒）。
     * 扫描条件：
     * <ul>
     *   <li>Redis device:status:{sn} online=0（设备已离线）</li>
     *   <li>Redis device:offline:time:{sn} 距今 &gt; terminateDelaySeconds（离线超时）</li>
     *   <li>DB 中存在 orderStatus=CHARGING 的订单关联此充电桩</li>
     * </ul>
     * 覆盖以下遗漏场景：
     * <ul>
     *   <li>心跳超时扫描时 charger 尚不存在于 DB</li>
     *   <li>handleOffline 被调用但 checkHeartbeatTimeout 未覆盖到</li>
     *   <li>Redis 离线标记存在但心跳超时扫描因异常跳过</li>
     * </ul>
     * </p>
     */
    @Scheduled(fixedDelayString = "${device.offline.orphan-scan-interval-ms:60000}")
    public void reconcileOrphanOrders() {
        long now = System.currentTimeMillis();
        long terminateDelayMs = offlineConfig.getTerminateDelaySeconds() * 1000L;

        try {
            log.debug("[孤儿订单扫描] 开始扫描...");
            int terminatedCount = 0;

            // 1. 扫描所有设备离线时间标记
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(DeviceConstants.REDIS_KEY_OFFLINE_TIME + "*")
                    .count(100)
                    .build();
            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    String offlineTimeKey = cursor.next();
                    try {
                        // 从 key 提取 SN（格式: device:offline:time:CHARGER-001）
                        String sn = offlineTimeKey.substring(
                                DeviceConstants.REDIS_KEY_OFFLINE_TIME.length());

                        // 2. 检查设备是否仍在线（在线则跳过）
                        String statusKey = DeviceConstants.REDIS_KEY_DEVICE_STATUS + sn;
                        Object onlineObj = redisTemplate.opsForHash().get(statusKey,
                                DeviceConstants.FIELD_ONLINE);
                        boolean isOnline = onlineObj != null && "1".equals(onlineObj.toString());
                        if (isOnline) {
                            // 设备已恢复在线，清除离线时间标记
                            redisTemplate.delete(offlineTimeKey);
                            continue;
                        }

                        // 3. 检查离线时长是否超过终止延迟阈值
                        Object offlineTimeObj = redisTemplate.opsForValue().get(offlineTimeKey);
                        if (offlineTimeObj == null) {
                            continue;
                        }
                        long offlineTime = Long.parseLong(offlineTimeObj.toString());
                        long offlineDuration = now - offlineTime;
                        if (offlineDuration < terminateDelayMs) {
                            continue; // 未到终止延迟阈值，等待
                        }

                        // 4. 查 charger 并终止 CHARGING 订单
                        Charger charger = chargerMapper.selectOne(
                                new LambdaQueryWrapper<Charger>().eq(Charger::getSn, sn)
                        );
                        if (charger == null) {
                            continue;
                        }

                        var chargingOrders = chargeOrderMapper.selectList(
                                new LambdaQueryWrapper<ChargeOrder>()
                                        .eq(ChargeOrder::getChargerId, charger.getId())
                                        .eq(ChargeOrder::getOrderStatus, OrderStatusEnum.CHARGING.getCode())
                        );
                        boolean hasChargingOrPending = false;

                        if (chargingOrders != null && !chargingOrders.isEmpty()) {
                            hasChargingOrPending = true;
                            for (ChargeOrder order : chargingOrders) {
                                try {
                                    log.info("[孤儿订单扫描] SN: {}, orderNo: {}, 离线时长={}ms, 兜底终止",
                                            sn, order.getOrderNo(), offlineDuration);
                                    orderService.autoTerminateOrder(order.getOrderNo(),
                                            "ORPHAN_ORDER_RECONCILE");
                                    terminatedCount++;
                                } catch (Exception e) {
                                    log.error("[孤儿订单扫描] orderNo: {} 终止异常", order.getOrderNo(), e);
                                }
                            }
                        }

                        // 5. 兜底清理 AWAITING_DEVICE 订单（启桩等待中设备离线）
                        //    AWAITING_DEVICE 订单无电量消耗，直接取消即可，无需计费
                        var awaitingOrders = chargeOrderMapper.selectList(
                                new LambdaQueryWrapper<ChargeOrder>()
                                        .eq(ChargeOrder::getChargerId, charger.getId())
                                        .eq(ChargeOrder::getOrderStatus,
                                                OrderStatusEnum.AWAITING_DEVICE.getCode())
                        );
                        if (awaitingOrders != null && !awaitingOrders.isEmpty()) {
                            hasChargingOrPending = true;
                            for (ChargeOrder order : awaitingOrders) {
                                try {
                                    log.info("[孤儿订单扫描] SN: {}, 取消 AWAITING_DEVICE 订单 orderNo: {}, 离线时长={}ms",
                                            sn, order.getOrderNo(), offlineDuration);
                                    order.setOrderStatus(OrderStatusEnum.CANCELLED.getCode());
                                    chargeOrderMapper.updateById(order);
                                    terminatedCount++;
                                } catch (Exception e) {
                                    log.error("[孤儿订单扫描] AWAITING_DEVICE orderNo: {} 取消失败",
                                            order.getOrderNo(), e);
                                }
                            }
                        }

                        if (!hasChargingOrPending) {
                            // 无 CHARGING/ AWAITING_DEVICE 订单，清除离线时间标记
                            redisTemplate.delete(offlineTimeKey);
                        }
                    } catch (Exception e) {
                        log.error("[孤儿订单扫描] 处理 key={} 异常", offlineTimeKey, e);
                    }
                }
            }

            if (terminatedCount > 0) {
                log.info("[孤儿订单扫描] 完成，本次终止/取消 {} 个孤儿订单", terminatedCount);
            }
        } catch (Exception e) {
            log.error("[孤儿订单扫描] 扫描任务异常", e);
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
        String dataKey = DeviceConstants.REDIS_KEY_DEVICE_DATA + sn;
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

        // 旁路记录能量时间线到 Redis ZSET，用于精确计费的增量电量计算
        recordEnergyTimeline(sn, data);
    }

    /**
     * 旁路记录设备能量时间线到 Redis ZSET
     * <p>
     * 设备每 5 秒上报一次累计 energy 值，将其存入 ZSET 以便计费时获取每段分钟的实际增量。
     * ZSET 的 score 为时间戳，member 为 "timestamp:energyValue" 格式。
     * 设置 48 小时 TTL，定时清理过期数据防止无限增长。
     * 记录失败只打日志不影响主流程（best-effort 策略）。
     * </p>
     *
     * @param sn   设备SN
     * @param data 上报的实时数据
     */
    private void recordEnergyTimeline(String sn, Map<String, Object> data) {
        if (data == null || !data.containsKey(DeviceConstants.FIELD_ENERGY)
                || data.get(DeviceConstants.FIELD_ENERGY) == null) {
            return;
        }

        try {
            String timelineKey = DeviceConstants.REDIS_KEY_ENERGY_TIMELINE + sn;
            long timestamp = System.currentTimeMillis();
            String energyStr = data.get(DeviceConstants.FIELD_ENERGY).toString();
            double currentEnergy = Double.parseDouble(energyStr);

            // member 格式: "timestamp:energyValue"，score 为时间戳毫秒值
            String member = timestamp + ":" + energyStr;
            redisTemplate.opsForZSet().add(timelineKey, member, timestamp);

            // 设置过期时间
            redisTemplate.expire(timelineKey, DeviceConstants.ENERGY_TIMELINE_TTL_HOURS, TimeUnit.HOURS);

            // 清理过期数据（保留最近 TTL 小时内的记录）
            long cutoffTime = timestamp - TimeUnit.HOURS.toMillis(
                    DeviceConstants.ENERGY_TIMELINE_TTL_HOURS);
            redisTemplate.opsForZSet().removeRangeByScore(timelineKey, 0, cutoffTime);
        } catch (Exception e) {
            log.warn("[能量时间线] 记录失败 - SN: {}, error: {}", sn, e.getMessage());
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

        if (data.containsKey(DeviceConstants.FIELD_VOLTAGE) && data.get(DeviceConstants.FIELD_VOLTAGE) != null) {
            charger.setCurrentVoltage(new BigDecimal(data.get(DeviceConstants.FIELD_VOLTAGE).toString()));
        }
        if (data.containsKey(DeviceConstants.FIELD_CURRENT) && data.get(DeviceConstants.FIELD_CURRENT) != null) {
            charger.setCurrentCurrent(new BigDecimal(data.get(DeviceConstants.FIELD_CURRENT).toString()));
        }
        if (data.containsKey(DeviceConstants.FIELD_POWER) && data.get(DeviceConstants.FIELD_POWER) != null) {
            charger.setCurrentPower(new BigDecimal(data.get(DeviceConstants.FIELD_POWER).toString()));
        }
        if (data.containsKey(DeviceConstants.FIELD_ENERGY) && data.get(DeviceConstants.FIELD_ENERGY) != null) {
            charger.setChargedEnergy(new BigDecimal(data.get(DeviceConstants.FIELD_ENERGY).toString()));
        }
        if (data.containsKey(DeviceConstants.FIELD_TEMPERATURE) && data.get(DeviceConstants.FIELD_TEMPERATURE) != null) {
            charger.setTemperature(new BigDecimal(data.get(DeviceConstants.FIELD_TEMPERATURE).toString()));
        }
    }

    /**
     * 设备状态对账：确认 AWAITING_DEVICE 订单
     * <p>
     * 当设备主动上报 CHARGING 状态时，说明设备实际已开始充电。
     * 查询该充电桩上所有 AWAITING_DEVICE 的订单，自动转为 CHARGING 状态。
     * 解决指令响应丢失导致的"设备在充电但订单未确认"问题。
     * </p>
     *
     * @param chargerId 充电桩ID
     * @param sn        设备SN
     */
    private void reconcilePendingOrders(Long chargerId, String sn) {
        try {
            // 查询该充电桩上状态为 AWAITING_DEVICE 的订单
            // （AWAITING_DEVICE 专用于启桩等待阶段，不会与已计费账单 PENDING_CONFIRM 冲突）
            var orders = chargeOrderMapper.selectList(
                    new LambdaQueryWrapper<ChargeOrder>()
                            .eq(ChargeOrder::getChargerId, chargerId)
                            .eq(ChargeOrder::getOrderStatus, OrderStatusEnum.AWAITING_DEVICE.getCode())
                            .orderByDesc(ChargeOrder::getStartTime)
            );

            if (orders == null || orders.isEmpty()) {
                return; // 无待确认订单，无需对账
            }

            for (ChargeOrder order : orders) {
                log.info("[状态对账] 设备上报 CHARGING，自动确认订单 - orderNo: {}, chargerId: {}, SN: {}",
                        order.getOrderNo(), chargerId, sn);

                // 更新订单状态为 CHARGING
                order.setOrderStatus(OrderStatusEnum.CHARGING.getCode());
                chargeOrderMapper.updateById(order);

                // WebSocket 推送订单已确认
                if (chargeEventPublisher != null) {
                    try {
                        chargeEventPublisher.publishChargeStart(
                                order.getUserId(), order.getOrderNo(), order.getChargerId());
                        chargeEventPublisher.publishCommandStatus(
                                order.getUserId(), order.getOrderNo(), "SUCCESS", "充电已开始");
                    } catch (Exception e) {
                        log.warn("[状态对账] WebSocket 推送失败 - orderNo: {}", order.getOrderNo(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[状态对账] 对账处理异常 - chargerId: {}, SN: {}", chargerId, sn, e);
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
