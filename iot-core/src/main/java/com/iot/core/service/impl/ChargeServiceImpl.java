package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.common.constant.DeviceConstants;
import com.iot.common.constant.OrderConstants;
import com.iot.common.enums.DeviceStatusEnum;
import com.iot.common.enums.OrderStatusEnum;
import com.iot.common.enums.PayStatusEnum;
import com.iot.common.exception.BusinessException;
import com.iot.common.model.CommandResult;
import com.iot.common.util.SnowflakeIdUtil;
import com.iot.core.dto.response.ChargeStatusVO;
import com.iot.core.entity.ChargeOrder;
import com.iot.core.entity.Charger;
import com.iot.core.event.DeviceDataReportEvent;
import com.iot.core.mapper.ChargeOrderMapper;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mq.producer.ChargeEventProducer;
import com.iot.core.service.ChargeEventPublisher;
import com.iot.core.service.ChargeService;
import com.iot.core.service.DeviceService;
import com.iot.core.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 充电服务实现类
 * <p>
 * 实现扫码启桩、结束充电、实时状态查询的核心业务逻辑。
 * 使用 Redisson 分布式锁防止并发抢桩，MQTT 指令下发采用 best-effort 策略。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeServiceImpl implements ChargeService {

    /** Redis Key 和 Field 常量统一使用 DeviceConstants */

    /** 分布式锁等待时间（秒） */
    private static final long LOCK_WAIT_TIME = 3;
    /** 分布式锁持有时间（秒），超时自动释放防止死锁 */
    private static final long LOCK_LEASE_TIME = 30;

    private final ChargerMapper chargerMapper;
    private final ChargeOrderMapper chargeOrderMapper;
    private final DeviceService deviceService;
    private final PricingService pricingService;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChargeEventProducer chargeEventProducer;

    /**
     * ChargeEventPublisher 由 iot-api 模块实现（WebSocket 推送）。
     * required=false：当 iot-api 未加载时（如单元测试），此依赖为 null。
     */
    @Autowired(required = false)
    private ChargeEventPublisher chargeEventPublisher;

    // ==================== 扫码启桩 ====================

    /**
     * 扫码启桩主流程
     * <p>
     * 使用 Redis 分布式锁防止同一充电桩被多个用户同时抢占。
     * MQTT 指令下发失败不阻塞订单创建（设备上线后可通过补充逻辑处理）。
     * 先充后付模式：订单直接进入 CHARGING 状态，支付在充电完成后处理。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChargeOrder startCharge(Long userId, Long chargerId) {
        log.info("[启桩] 用户 {} 请求启动充电桩 {}", userId, chargerId);

        // 1. 查询充电桩
        Charger charger = chargerMapper.selectById(chargerId);
        if (charger == null) {
            throw new BusinessException(404, "充电桩不存在");
        }

        // 2. 状态前置校验（锁外快速失败）
        int currentStatus = charger.getStatus();
        if (currentStatus != DeviceStatusEnum.IDLE.getCode()
                && currentStatus != DeviceStatusEnum.LOCKED.getCode()) {
            throw new BusinessException(409, "充电桩当前无法使用，状态: "
                    + DeviceStatusEnum.fromCode(currentStatus).getDesc());
        }

        // 3. 获取分布式锁
        RLock lock = redissonClient.getLock(DeviceConstants.REDIS_KEY_CHARGE_LOCK + chargerId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(429, "设备繁忙，请稍后再试");
            }

            // 4. 锁内二次校验（防 ABA 问题）
            Charger freshCharger = chargerMapper.selectById(chargerId);
            int freshStatus = freshCharger.getStatus();
            if (freshStatus != DeviceStatusEnum.IDLE.getCode()
                    && freshStatus != DeviceStatusEnum.LOCKED.getCode()) {
                throw new BusinessException(409, "设备状态已变更，请刷新重试");
            }

            // 记录设备当前状态，用于失败时回滚
            int prevChargerStatus = freshCharger.getStatus();
            String sn = freshCharger.getSn();
            String statusKey = DeviceConstants.REDIS_KEY_DEVICE_STATUS + sn;

            // 5. 创建充电订单 — 状态为 PENDING_CONFIRM（等待设备确认）
            //    注意：充电桩状态暂不修改，等设备确认后再更新
            String orderNo = OrderConstants.ORDER_NO_PREFIX + SnowflakeIdUtil.nextIdStr();
            ChargeOrder order = new ChargeOrder();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setChargerId(chargerId);
            order.setStationId(freshCharger.getStationId());
            order.setStartTime(LocalDateTime.now());
            order.setOrderStatus(OrderStatusEnum.AWAITING_DEVICE.getCode());  // ← 等待设备确认
            order.setPayStatus(PayStatusEnum.UNPAID.getCode());
            chargeOrderMapper.insert(order);

            log.info("[启桩] 订单已创建（待确认） - orderNo: {}, chargerId: {}", orderNo, chargerId);

            // 6. MQTT 下发启动指令 + 同步等待 3 秒（混合模式核心）
            Map<String, Object> cmdParams = new HashMap<>();
            cmdParams.put("orderNo", orderNo);
            cmdParams.put("userId", userId);

            CommandResult result = deviceService.sendCommandAndWait(
                    sn, "START_CHARGE", cmdParams, orderNo, userId, 3000);

            if (result == null) {
                // 设备不在线：取消订单，充电桩状态保持不变（无需回滚）
                log.warn("[启桩] 设备不在线，取消订单 - orderNo: {}, sn: {}", orderNo, sn);
                order.setOrderStatus(OrderStatusEnum.CANCELLED.getCode());
                chargeOrderMapper.updateById(order);
                throw new BusinessException(503, "设备不在线，无法启动充电");
            }

            switch (result) {
                case SUCCESS -> {
                    // 设备确认启动成功 → 更新充电桩为充电中 + 订单为 CHARGING
                    log.info("[启桩] 设备确认启动成功 - orderNo: {}, chargerId: {}", orderNo, chargerId);
                    freshCharger.setStatus(DeviceStatusEnum.CHARGING.getCode());
                    chargerMapper.updateById(freshCharger);
                    redisTemplate.opsForHash().put(statusKey, "status",
                            String.valueOf(DeviceStatusEnum.CHARGING.getCode()));

                    order.setOrderStatus(OrderStatusEnum.CHARGING.getCode());
                    chargeOrderMapper.updateById(order);

                    // 推送充电开始事件
                    publishChargeStart(userId, orderNo, chargerId);
                    chargeEventProducer.publishChargeStartEvent(order);
                }

                case DEVICE_ERROR -> {
                    // 设备拒绝执行 → 取消订单
                    log.warn("[启桩] 设备拒绝启动 - orderNo: {}, chargerId: {}", orderNo, chargerId);
                    order.setOrderStatus(OrderStatusEnum.CANCELLED.getCode());
                    chargeOrderMapper.updateById(order);
                    throw new BusinessException(500, "设备启动失败，订单已取消");
                }

                case TIMEOUT -> {
                    // 同步等待超时 → 订单保持 PENDING_CONFIRM，异步补偿继续
                    log.warn("[启桩] 同步等待超时，转入异步补偿 - orderNo: {}, chargerId: {}",
                            orderNo, chargerId);
                    // 推送"启动中"状态
                    publishCommandStatus(userId, orderNo, "PENDING", "设备正在启动中，请稍候...");
                }
            }

            return order;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "系统异常，获取锁被中断");
        } finally {
            // 释放锁（由于设置了 leaseTime，超时也会自动释放）
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ==================== 结束充电 ====================

    /**
     * 结束充电
     * <p>
     * 对于 CHARGING 状态订单：下发停止指令、计费、更新状态。
     * 对于 AWAITING_DEVICE 状态订单：直接取消（设备尚未确认启动，无需下发停止指令）。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChargeOrder stopCharge(Long userId, String orderNo) {
        log.info("[停桩] 用户 {} 请求停止充电 - orderNo: {}", userId, orderNo);

        // 1. 查找并校验订单
        ChargeOrder order = findAndValidateOrder(orderNo, userId);

        // 1.5 特殊处理：AWAITING_DEVICE 订单直接取消，无需下发指令和计费
        if (order.getOrderStatus() == OrderStatusEnum.AWAITING_DEVICE.getCode()) {
            log.info("[停桩] AWAITING_DEVICE 订单直接取消 - orderNo: {}", orderNo);
            order.setOrderStatus(OrderStatusEnum.CANCELLED.getCode());
            chargeOrderMapper.updateById(order);
            // 推送取消通知
            publishCommandStatus(userId, orderNo, "CANCELLED", "订单已取消");
            return order;
        }

        // 2. 查找充电桩
        Charger charger = chargerMapper.selectById(order.getChargerId());
        if (charger == null) {
            throw new BusinessException(404, "充电桩不存在");
        }

        // 3. 下发停止充电指令
        Map<String, Object> params = new HashMap<>();
        params.put("orderNo", orderNo);
        boolean cmdSent = deviceService.sendCommand(charger.getSn(), "STOP_CHARGE", params);
        if (!cmdSent) {
            log.warn("[停桩] MQTT 停止指令下发失败 - orderNo: {}", orderNo);
        }

        // 4. 从 Redis 读取设备最后上报的充电数据
        Map<Object, Object> deviceData = redisTemplate.opsForHash()
                .entries(DeviceConstants.REDIS_KEY_DEVICE_DATA + charger.getSn());
        BigDecimal finalEnergy = BigDecimal.ZERO;
        if (deviceData != null && deviceData.containsKey("energy")) {
            finalEnergy = new BigDecimal(deviceData.get("energy").toString());
        }

        // 5. 计费（传入设备SN用于获取能量时间线增量数据）
        LocalDateTime endTime = LocalDateTime.now();
        PricingService.FeeDetail feeDetail = pricingService.calculateExactFee(
                charger.getStationId(), order.getStartTime(), endTime, finalEnergy, charger.getSn());

        // 6. 更新订单
        order.setEndTime(endTime);
        order.setChargedEnergy(finalEnergy);
        order.setElectricityFee(feeDetail.electricityFee());
        order.setServiceFee(feeDetail.serviceFee());
        order.setTotalAmount(feeDetail.totalAmount());
        order.setOrderStatus(OrderStatusEnum.PENDING_CONFIRM.getCode());
        chargeOrderMapper.updateById(order);

        // 7. 更新充电桩状态为空闲
        charger.setStatus(DeviceStatusEnum.IDLE.getCode());
        charger.setChargedEnergy(finalEnergy);
        chargerMapper.updateById(charger);

        // 同步更新 Redis
        String statusKey = DeviceConstants.REDIS_KEY_DEVICE_STATUS + charger.getSn();
        redisTemplate.opsForHash().put(statusKey, "status",
                String.valueOf(DeviceStatusEnum.IDLE.getCode()));

        log.info("[停桩] 充电完成 - orderNo: {}, 电量: {}kWh, 总费用: {}元",
                orderNo, finalEnergy, feeDetail.totalAmount());

        // 8. 推送充电结束事件
        publishChargeStop(userId, orderNo, feeDetail.totalAmount());

        // 9. 发送 RocketMQ 事件
        chargeEventProducer.publishChargeEndEvent(order);

        return order;
    }

    // ==================== 实时状态查询 ====================

    /**
     * 获取充电实时状态
     * <p>
     * 从订单表获取基础信息，从 Redis 读取设备的实时电力数据，
     * 调用计费服务估算当前已产生的费用。
     * </p>
     */
    @Override
    public ChargeStatusVO getChargeStatus(String orderNo) {
        // 1. 查找订单
        ChargeOrder order = chargeOrderMapper.selectOne(
                new LambdaQueryWrapper<ChargeOrder>().eq(ChargeOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        // 2. 查找充电桩以获取 SN
        Charger charger = chargerMapper.selectById(order.getChargerId());
        if (charger == null) {
            throw new BusinessException(404, "充电桩不存在");
        }

        // 3. 从 Redis 读取实时数据
        Map<Object, Object> deviceData = redisTemplate.opsForHash()
                .entries(DeviceConstants.REDIS_KEY_DEVICE_DATA + charger.getSn());

        BigDecimal voltage = null;
        BigDecimal current = null;
        BigDecimal currentPower = null;
        BigDecimal chargedEnergy = order.getChargedEnergy();
        BigDecimal temperature = null;

        if (deviceData != null && !deviceData.isEmpty()) {
            if (deviceData.containsKey("voltage") && deviceData.get("voltage") != null) {
                voltage = new BigDecimal(deviceData.get("voltage").toString());
            }
            if (deviceData.containsKey("current") && deviceData.get("current") != null) {
                current = new BigDecimal(deviceData.get("current").toString());
            }
            if (deviceData.containsKey("power") && deviceData.get("power") != null) {
                currentPower = new BigDecimal(deviceData.get("power").toString());
            }
            if (deviceData.containsKey("energy") && deviceData.get("energy") != null) {
                chargedEnergy = new BigDecimal(deviceData.get("energy").toString());
            }
            if (deviceData.containsKey("temperature") && deviceData.get("temperature") != null) {
                temperature = new BigDecimal(deviceData.get("temperature").toString());
            }
        }

        // 4. 计算已充时长
        long durationSeconds = 0;
        if (order.getStartTime() != null) {
            durationSeconds = Duration.between(order.getStartTime(), LocalDateTime.now()).getSeconds();
        }

        // 5. 估算费用
        BigDecimal estimatedAmount = pricingService.estimateFee(
                charger.getStationId(), chargedEnergy);

        return ChargeStatusVO.builder()
                .orderNo(order.getOrderNo())
                .orderStatus(order.getOrderStatus())
                .orderStatusDesc(OrderStatusEnum.fromCode(order.getOrderStatus()).getDesc())
                .startTime(order.getStartTime())
                .voltage(voltage)
                .current(current)
                .currentPower(currentPower)
                .chargedEnergy(chargedEnergy)
                .estimatedAmount(estimatedAmount)
                .durationSeconds(durationSeconds)
                .temperature(temperature)
                .build();
    }

    /**
     * 通过订单编号查找充电中的订单
     */
    @Override
    public ChargeOrder getChargingOrder(String orderNo) {
        return chargeOrderMapper.selectOne(
                new LambdaQueryWrapper<ChargeOrder>()
                        .eq(ChargeOrder::getOrderNo, orderNo)
                        .eq(ChargeOrder::getOrderStatus, OrderStatusEnum.CHARGING.getCode())
        );
    }

    // ==================== Spring Event Listener ====================

    /**
     * 监听设备数据上报事件，触发充电进度 WebSocket 推送
     * <p>
     * 当 DeviceServiceImpl 收到设备上报的实时数据时，
     * 通过 Spring Event 机制触发，将数据推送给正在充电的用户。
     * </p>
     */
    @EventListener
    public void handleDeviceDataReport(DeviceDataReportEvent event) {
        try {
            // 查找该充电桩上正在充电的订单
            ChargeOrder chargingOrder = chargeOrderMapper.selectOne(
                    new LambdaQueryWrapper<ChargeOrder>()
                            .eq(ChargeOrder::getChargerId, event.getChargerId())
                            .eq(ChargeOrder::getOrderStatus, OrderStatusEnum.CHARGING.getCode())
                            .orderByDesc(ChargeOrder::getStartTime)
                            .last("LIMIT 1")
            );

            if (chargingOrder == null) {
                return; // 没有正在充电的订单，无需推送
            }

            // 提取实时数据
            Map<String, Object> data = event.getData();
            BigDecimal chargedEnergy = data.containsKey("energy") && data.get("energy") != null
                    ? new BigDecimal(data.get("energy").toString())
                    : BigDecimal.ZERO;
            BigDecimal currentPower = data.containsKey("power") && data.get("power") != null
                    ? new BigDecimal(data.get("power").toString())
                    : BigDecimal.ZERO;
            BigDecimal voltage = data.containsKey("voltage") && data.get("voltage") != null
                    ? new BigDecimal(data.get("voltage").toString())
                    : null;
            BigDecimal current = data.containsKey("current") && data.get("current") != null
                    ? new BigDecimal(data.get("current").toString())
                    : null;

            // 计算估算费用
            BigDecimal estimatedAmount = pricingService.estimateFee(
                    chargingOrder.getStationId(), chargedEnergy);

            // 计算已充时长
            long durationSeconds = Duration.between(
                    chargingOrder.getStartTime(), LocalDateTime.now()).getSeconds();

            // 通过 ChargeEventPublisher 推送进度
            publishChargeProgress(chargingOrder.getUserId(), chargingOrder.getOrderNo(),
                    chargedEnergy, currentPower, estimatedAmount, durationSeconds,
                    voltage, current);

        } catch (Exception e) {
            log.warn("[充电进度推送] 处理设备数据上报事件失败 - chargerId: {}, error: {}",
                    event.getChargerId(), e.getMessage());
        }
    }

    // ==================== 私有推送方法 ====================

    /**
     * 推送充电进度（null-safe）
     */
    private void publishChargeProgress(Long userId, String orderNo, BigDecimal chargedEnergy,
                                       BigDecimal currentPower, BigDecimal estimatedAmount,
                                       Long durationSeconds,
                                       BigDecimal voltage, BigDecimal current) {
        if (chargeEventPublisher != null) {
            chargeEventPublisher.publishChargeProgress(
                    userId, orderNo, chargedEnergy, currentPower, estimatedAmount,
                    durationSeconds, voltage, current);
        }
    }

    /**
     * 推送充电开始（null-safe）
     */
    private void publishChargeStart(Long userId, String orderNo, Long chargerId) {
        if (chargeEventPublisher != null) {
            chargeEventPublisher.publishChargeStart(userId, orderNo, chargerId);
        }
    }

    /**
     * 推送充电停止（null-safe）
     */
    private void publishChargeStop(Long userId, String orderNo, BigDecimal totalAmount) {
        if (chargeEventPublisher != null) {
            chargeEventPublisher.publishChargeStop(userId, orderNo, totalAmount);
        }
    }

    /**
     * 推送指令执行状态（null-safe）
     * <p>
     * 用于混合模式启桩流程中向用户推送指令执行进度。
     * </p>
     *
     * @param userId  用户ID
     * @param orderNo 订单编号
     * @param status  指令状态（PENDING/SUCCESS/FAILED/TIMEOUT）
     * @param message 提示消息
     */
    private void publishCommandStatus(Long userId, String orderNo, String status, String message) {
        if (chargeEventPublisher != null) {
            chargeEventPublisher.publishCommandStatus(userId, orderNo, status, message);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 查找并校验订单归属
     *
     * @param orderNo 订单编号
     * @param userId  用户ID（用于校验归属）
     * @return 充电订单
     * @throws BusinessException 订单不存在、不属于当前用户或状态不为充电中时抛出
     */
    private ChargeOrder findAndValidateOrder(String orderNo, Long userId) {
        ChargeOrder order = chargeOrderMapper.selectOne(
                new LambdaQueryWrapper<ChargeOrder>().eq(ChargeOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该订单");
        }
        // 仅允许 CHARGING（正常充电中）和 AWAITING_DEVICE（等待设备确认）状态操作
        if (order.getOrderStatus() != OrderStatusEnum.CHARGING.getCode()
                && order.getOrderStatus() != OrderStatusEnum.AWAITING_DEVICE.getCode()) {
            throw new BusinessException(409, "订单状态不允许此操作，当前状态: "
                    + OrderStatusEnum.fromCode(order.getOrderStatus()).getDesc());
        }
        return order;
    }
}
