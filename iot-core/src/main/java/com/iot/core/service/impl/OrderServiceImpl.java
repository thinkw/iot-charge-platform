package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.common.enums.DeviceStatusEnum;
import com.iot.common.enums.OrderStatusEnum;
import com.iot.common.enums.PayStatusEnum;
import com.iot.common.exception.BusinessException;
import com.iot.common.model.PageResult;
import com.iot.core.dto.response.OrderVO;
import com.iot.core.entity.ChargeOrder;
import com.iot.core.entity.Charger;
import com.iot.core.entity.Station;
import com.iot.core.mapper.ChargeOrderMapper;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mapper.StationMapper;
import com.iot.core.mq.producer.ChargeEventProducer;
import com.iot.core.service.DeviceService;
import com.iot.core.service.OrderService;
import com.iot.core.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 * <p>
 * 提供订单查询、支付和退款的具体实现。
 * 使用 MyBatis-Plus 乐观锁（version 字段）防止并发支付冲突。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ChargeOrderMapper chargeOrderMapper;
    private final ChargerMapper chargerMapper;
    private final StationMapper stationMapper;
    private final ChargeEventProducer chargeEventProducer;
    private final PricingService pricingService;
    private final DeviceService deviceService;

    /**
     * 分页查询用户订单列表
     * <p>
     * 支持多条件筛选：订单状态、时间范围。
     * 批量查询关联的充电桩和充电站名称，避免 N+1 查询。
     * </p>
     */
    @Override
    public PageResult<OrderVO> listOrders(Long userId, int page, int size,
                                           Integer orderStatus, LocalDateTime startTime, LocalDateTime endTime) {
        // 构建查询条件
        LambdaQueryWrapper<ChargeOrder> queryWrapper = new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getUserId, userId)
                .orderByDesc(ChargeOrder::getCreateTime);

        if (orderStatus != null) {
            queryWrapper.eq(ChargeOrder::getOrderStatus, orderStatus);
        }
        if (startTime != null) {
            queryWrapper.ge(ChargeOrder::getCreateTime, startTime);
        }
        if (endTime != null) {
            queryWrapper.le(ChargeOrder::getCreateTime, endTime);
        }

        Page<ChargeOrder> pageResult = chargeOrderMapper.selectPage(Page.of(page, size), queryWrapper);
        List<ChargeOrder> orders = pageResult.getRecords();

        if (orders.isEmpty()) {
            return PageResult.of(List.of(), pageResult.getTotal(), page, size);
        }

        // 批量查询关联的充电桩和充电站（避免 N+1）
        List<Long> chargerIds = orders.stream().map(ChargeOrder::getChargerId).distinct().toList();
        Map<Long, Charger> chargerMap = chargerMapper.selectBatchIds(chargerIds)
                .stream().collect(Collectors.toMap(Charger::getId, c -> c, (a, b) -> a));

        List<Long> stationIds = orders.stream().map(ChargeOrder::getStationId).distinct().toList();
        Map<Long, Station> stationMap = stationMapper.selectBatchIds(stationIds)
                .stream().collect(Collectors.toMap(Station::getId, s -> s, (a, b) -> a));

        // 转换为 VO
        List<OrderVO> voList = orders.stream().map(order -> {
            Charger charger = chargerMap.get(order.getChargerId());
            Station station = stationMap.get(order.getStationId());
            return buildOrderVO(order, charger, station);
        }).toList();

        return PageResult.of(voList, pageResult.getTotal(), page, size);
    }

    /**
     * 获取订单详情
     * <p>
     * 当 userId 为 null 时表示管理端访问，跳过归属校验。
     * </p>
     */
    @Override
    public OrderVO getOrderDetail(Long orderId, Long userId) {
        ChargeOrder order = chargeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        // 管理端（userId为null）跳过归属校验
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权查看该订单");
        }

        Charger charger = chargerMapper.selectById(order.getChargerId());
        Station station = stationMapper.selectById(order.getStationId());

        return buildOrderVO(order, charger, station);
    }

    /**
     * 模拟支付
     * <p>
     * 支付条件：订单状态为已完成(COMPLETED) 且 支付状态为未支付(UNPAID)。
     * 使用乐观锁（version字段）更新，如并发导致更新失败则抛出异常。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(String orderNo, Long userId) {
        ChargeOrder order = chargeOrderMapper.selectOne(
                new LambdaQueryWrapper<ChargeOrder>().eq(ChargeOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该订单");
        }
        if (order.getPayStatus() != PayStatusEnum.UNPAID.getCode()) {
            throw new BusinessException(409, "订单支付状态异常，当前状态: "
                    + PayStatusEnum.fromCode(order.getPayStatus()).getDesc());
        }
        if (order.getOrderStatus() != OrderStatusEnum.COMPLETED.getCode()) {
            throw new BusinessException(409, "订单未完成，无法支付，当前状态: "
                    + OrderStatusEnum.fromCode(order.getOrderStatus()).getDesc());
        }

        // 使用乐观锁更新
        order.setPayStatus(PayStatusEnum.PAID.getCode());
        order.setPayTime(LocalDateTime.now());
        int updated = chargeOrderMapper.updateById(order); // @Version 自动检查版本号
        if (updated == 0) {
            throw new BusinessException(409, "支付失败，订单已被其他操作更新，请刷新重试");
        }

        log.info("[支付] 订单 {} 支付成功，金额: {} 元", orderNo, order.getTotalAmount());

        // 发送支付完成事件
        chargeEventProducer.publishPayCompletedEvent(orderNo, order.getTotalAmount());
    }

    /**
     * 申请退款
     * <p>
     * 退款条件：支付状态为已支付(PAID)。
     * 模拟退款，直接更新状态为已退款(REFUNDED)。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(String orderNo, Long userId) {
        ChargeOrder order = chargeOrderMapper.selectOne(
                new LambdaQueryWrapper<ChargeOrder>().eq(ChargeOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该订单");
        }
        if (order.getPayStatus() != PayStatusEnum.PAID.getCode()) {
            throw new BusinessException(409, "订单不支持退款，当前支付状态: "
                    + PayStatusEnum.fromCode(order.getPayStatus()).getDesc());
        }

        order.setPayStatus(PayStatusEnum.REFUNDED.getCode());
        int updated = chargeOrderMapper.updateById(order);
        if (updated == 0) {
            throw new BusinessException(409, "退款失败，订单已被其他操作更新，请刷新重试");
        }

        log.info("[退款] 订单 {} 退款成功，金额: {} 元", orderNo, order.getTotalAmount());
    }

    // ==================== 管理端方法 ====================

    /**
     * 管理端：分页查询全量订单列表
     * <p>
     * 不限制 userId，支持多条件组合筛选。
     * 结果中包含充电桩名称和充电站名称。
     * </p>
     */
    @Override
    public PageResult<OrderVO> listAllOrders(Long userId, Long chargerId, Long stationId,
                                               Integer orderStatus, Integer payStatus,
                                               LocalDateTime startTime, LocalDateTime endTime,
                                               int page, int size) {
        // 构建多条件查询
        LambdaQueryWrapper<ChargeOrder> queryWrapper = new LambdaQueryWrapper<ChargeOrder>()
                .orderByDesc(ChargeOrder::getCreateTime);

        if (userId != null) {
            queryWrapper.eq(ChargeOrder::getUserId, userId);
        }
        if (chargerId != null) {
            queryWrapper.eq(ChargeOrder::getChargerId, chargerId);
        }
        if (stationId != null) {
            queryWrapper.eq(ChargeOrder::getStationId, stationId);
        }
        if (orderStatus != null) {
            queryWrapper.eq(ChargeOrder::getOrderStatus, orderStatus);
        }
        if (payStatus != null) {
            queryWrapper.eq(ChargeOrder::getPayStatus, payStatus);
        }
        if (startTime != null) {
            queryWrapper.ge(ChargeOrder::getCreateTime, startTime);
        }
        if (endTime != null) {
            queryWrapper.le(ChargeOrder::getCreateTime, endTime);
        }

        Page<ChargeOrder> pageResult = chargeOrderMapper.selectPage(Page.of(page, size), queryWrapper);
        List<ChargeOrder> orders = pageResult.getRecords();

        if (orders.isEmpty()) {
            return PageResult.of(List.of(), pageResult.getTotal(), page, size);
        }

        // 批量查询关联名称
        Map<Long, Charger> chargerMap = chargerMapper.selectBatchIds(
                orders.stream().map(ChargeOrder::getChargerId).distinct().toList())
                .stream().collect(Collectors.toMap(Charger::getId, c -> c, (a, b) -> a));
        Map<Long, Station> stationMap = stationMapper.selectBatchIds(
                orders.stream().map(ChargeOrder::getStationId).distinct().toList())
                .stream().collect(Collectors.toMap(Station::getId, s -> s, (a, b) -> a));

        List<OrderVO> voList = orders.stream().map(order -> {
            Charger charger = chargerMap.get(order.getChargerId());
            Station station = stationMap.get(order.getStationId());
            return buildOrderVO(order, charger, station);
        }).toList();

        return PageResult.of(voList, pageResult.getTotal(), page, size);
    }

    /**
     * 管理端：手动结束异常订单
     * <p>
     * 用于处理因设备离线、系统异常等原因未能正常结束的订单。
     * 先尝试下发停止指令（设备在线时有效），再计算费用并更新状态。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceEndOrder(String orderNo, Long operatorId, String reason) {
        // 1. 查找并校验订单
        ChargeOrder order = chargeOrderMapper.selectOne(
                new LambdaQueryWrapper<ChargeOrder>().eq(ChargeOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (order.getOrderStatus() != OrderStatusEnum.CHARGING.getCode()
                && order.getOrderStatus() != OrderStatusEnum.ABNORMAL.getCode()) {
            throw new BusinessException(409, "订单状态不允许手动结束，当前状态: "
                    + OrderStatusEnum.fromCode(order.getOrderStatus()).getDesc());
        }

        // 2. 查找充电桩
        Charger charger = chargerMapper.selectById(order.getChargerId());
        if (charger == null) {
            throw new BusinessException(404, "关联充电桩不存在");
        }

        // 3. 尝试下发停止指令（best-effort）
        Map<String, Object> params = new HashMap<>();
        params.put("orderNo", orderNo);
        params.put("forceEnd", true);
        params.put("reason", reason);
        deviceService.sendCommand(charger.getSn(), "STOP_CHARGE", params);

        // 4. 计算费用
        LocalDateTime endTime = LocalDateTime.now();
        BigDecimal finalEnergy = order.getChargedEnergy() != null
                ? order.getChargedEnergy() : BigDecimal.ZERO;
        PricingService.FeeDetail feeDetail = pricingService.calculateExactFee(
                charger.getStationId(), order.getStartTime(), endTime, finalEnergy);

        // 5. 更新订单
        order.setEndTime(endTime);
        order.setChargedEnergy(finalEnergy);
        order.setElectricityFee(feeDetail.electricityFee());
        order.setServiceFee(feeDetail.serviceFee());
        order.setTotalAmount(feeDetail.totalAmount());
        order.setOrderStatus(OrderStatusEnum.COMPLETED.getCode());
        order.setCancelReason(reason);
        chargeOrderMapper.updateById(order);

        // 6. 恢复充电桩状态为空闲
        charger.setStatus(DeviceStatusEnum.IDLE.getCode());
        charger.setChargedEnergy(finalEnergy);
        chargerMapper.updateById(charger);

        log.info("[管理端-强制结束] 操作人: {}, orderNo: {}, 原因: {}, 电量: {}kWh, 费用: {}元",
                operatorId, orderNo, reason, finalEnergy, feeDetail.totalAmount());

        // 7. 发送充电结束事件
        chargeEventProducer.publishChargeEndEvent(order);
    }

    /**
     * 管理端：管理员退款
     * <p>
     * 允许管理员直接对已支付订单操作退款，记录退款原因。
     * 不校验 userId 归属。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminRefundOrder(String orderNo, Long operatorId, String reason) {
        ChargeOrder order = chargeOrderMapper.selectOne(
                new LambdaQueryWrapper<ChargeOrder>().eq(ChargeOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (order.getPayStatus() == PayStatusEnum.REFUNDED.getCode()) {
            throw new BusinessException(409, "订单已退款，无需重复操作");
        }
        if (order.getPayStatus() != PayStatusEnum.PAID.getCode()) {
            throw new BusinessException(409, "订单不支持退款，当前支付状态: "
                    + PayStatusEnum.fromCode(order.getPayStatus()).getDesc());
        }

        order.setPayStatus(PayStatusEnum.REFUNDED.getCode());
        order.setCancelReason(reason);
        int updated = chargeOrderMapper.updateById(order);
        if (updated == 0) {
            throw new BusinessException(409, "退款失败，订单已被其他操作更新，请刷新重试");
        }

        log.info("[管理端-退款] 操作人: {}, orderNo: {}, 原因: {}, 金额: {}元",
                operatorId, orderNo, reason, order.getTotalAmount());
    }

    // ==================== 私有方法 ====================

    /**
     * 构建 OrderVO（避免重复代码）
     */
    private OrderVO buildOrderVO(ChargeOrder order, Charger charger, Station station) {
        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .chargerId(order.getChargerId())
                .chargerName(charger != null ? charger.getName() : "未知")
                .stationId(order.getStationId())
                .stationName(station != null ? station.getName() : "未知")
                .startTime(order.getStartTime())
                .endTime(order.getEndTime())
                .chargedEnergy(order.getChargedEnergy())
                .totalAmount(order.getTotalAmount())
                .electricityFee(order.getElectricityFee())
                .serviceFee(order.getServiceFee())
                .payStatus(order.getPayStatus())
                .payStatusDesc(PayStatusEnum.fromCode(order.getPayStatus()).getDesc())
                .orderStatus(order.getOrderStatus())
                .orderStatusDesc(OrderStatusEnum.fromCode(order.getOrderStatus()).getDesc())
                .payTime(order.getPayTime())
                .createTime(order.getCreateTime())
                .build();
    }
}
