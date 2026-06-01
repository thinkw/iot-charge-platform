package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.common.constant.OrderConstants;
import com.iot.common.enums.DeviceStatusEnum;
import com.iot.common.exception.BusinessException;
import com.iot.common.util.SnowflakeIdUtil;
import com.iot.core.dto.request.CreateReservationRequest;
import com.iot.core.entity.Charger;
import com.iot.core.entity.ReservationOrder;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mapper.ReservationOrderMapper;
import com.iot.core.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 预约服务实现类
 * <p>
 * 实现充电桩预约的创建和取消逻辑。
 * 预约时段使用前闭后开区间 [startTime, endTime) 判断冲突。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    /** 预约订单编号前缀 */
    private static final String RESERVATION_PREFIX = "RES";
    /** 分布式锁 Key 前缀 */
    private static final String LOCK_KEY_PREFIX = "reservation:lock:";

    /** 默认押金金额（元） */
    private static final BigDecimal DEFAULT_DEPOSIT = BigDecimal.valueOf(30.00);
    /** 默认违约金（元） */
    private static final BigDecimal DEFAULT_PENALTY = BigDecimal.valueOf(10.00);

    /** 预约状态：待使用 */
    private static final int STATUS_PENDING = 0;
    /** 预约状态：已取消 */
    private static final int STATUS_CANCELLED = 2;

    private final ReservationOrderMapper reservationOrderMapper;
    private final ChargerMapper chargerMapper;
    private final RedissonClient redissonClient;

    /**
     * 创建预约订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createReservation(Long userId, CreateReservationRequest request) {
        log.info("[创建预约] userId: {}, chargerId: {}, date: {}, {}-{}",
                userId, request.getChargerId(), request.getReserveDate(),
                request.getStartTime(), request.getEndTime());

        // 1. 校验充电桩
        Charger charger = chargerMapper.selectById(request.getChargerId());
        if (charger == null) {
            throw new BusinessException(404, "充电桩不存在");
        }
        if (charger.getStatus() != DeviceStatusEnum.IDLE.getCode()) {
            throw new BusinessException(409, "充电桩当前不可预约，状态: "
                    + DeviceStatusEnum.fromCode(charger.getStatus()).getDesc());
        }

        // 2. 校验时间段（结束时间必须大于开始时间）
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException(400, "预约结束时间必须大于开始时间");
        }

        // 3. 检查时段冲突
        long conflictCount = reservationOrderMapper.selectCount(
                new LambdaQueryWrapper<ReservationOrder>()
                        .eq(ReservationOrder::getChargerId, request.getChargerId())
                        .eq(ReservationOrder::getReserveDate, request.getReserveDate())
                        .eq(ReservationOrder::getStatus, STATUS_PENDING) // 只检查待使用的预约
                        .and(w -> w
                                // 使用半开区间 [start, end) 判断冲突
                                .lt(ReservationOrder::getStartTime, request.getEndTime())
                                .ge(ReservationOrder::getEndTime, request.getStartTime())
                        )
        );

        if (conflictCount > 0) {
            throw new BusinessException(409, "该时段已被其他用户预约，请选择其他时段");
        }

        // 4. 获取分布式锁（防止并发创建同时间段预约）
        String lockKey = LOCK_KEY_PREFIX + request.getChargerId() + ":"
                + request.getReserveDate() + ":" + request.getStartTime();
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(429, "预约繁忙，请稍后再试");
            }

            // 5. 创建预约订单
            String orderNo = RESERVATION_PREFIX + SnowflakeIdUtil.nextIdStr();
            ReservationOrder order = new ReservationOrder();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setChargerId(request.getChargerId());
            order.setStationId(charger.getStationId());
            order.setReserveDate(request.getReserveDate());
            order.setStartTime(request.getStartTime());
            order.setEndTime(request.getEndTime());
            order.setDeposit(DEFAULT_DEPOSIT);
            order.setPenalty(DEFAULT_PENALTY);
            order.setStatus(STATUS_PENDING);
            order.setPayStatus(0); // 暂不支付押金
            reservationOrderMapper.insert(order);

            log.info("[创建预约] 预约成功 - orderNo: {}, chargerId: {}, date: {}, {}-{}",
                    orderNo, request.getChargerId(), request.getReserveDate(),
                    request.getStartTime(), request.getEndTime());

            return orderNo;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "系统异常，获取锁被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 取消预约
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelReservation(String orderNo, Long userId) {
        log.info("[取消预约] userId: {}, orderNo: {}", userId, orderNo);

        // 1. 查找预约订单
        ReservationOrder order = reservationOrderMapper.selectOne(
                new LambdaQueryWrapper<ReservationOrder>().eq(ReservationOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException(404, "预约订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该预约");
        }
        if (order.getStatus() != STATUS_PENDING) {
            throw new BusinessException(409, "预约订单状态不允许取消，当前状态: " + order.getStatus());
        }

        // 2. 更新预约状态
        order.setStatus(STATUS_CANCELLED);
        reservationOrderMapper.updateById(order);

        log.info("[取消预约] 预约已取消 - orderNo: {}", orderNo);
    }
}
