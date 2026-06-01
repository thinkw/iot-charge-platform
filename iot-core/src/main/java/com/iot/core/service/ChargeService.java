package com.iot.core.service;

import com.iot.core.dto.response.ChargeStatusVO;
import com.iot.core.entity.ChargeOrder;

/**
 * 充电服务接口
 * <p>
 * 提供扫码启桩、结束充电和实时状态查询功能。
 * 启桩流程涉及 Redis 分布式锁、MQTT 指令下发、订单创建等多个步骤。
 * </p>
 *
 * @author IoT Team
 */
public interface ChargeService {

    /**
     * 扫码启桩
     * <p>
     * 核心流程：
     * 1. 查询充电桩，校验状态（必须 IDLE 或 LOCKED）
     * 2. 获取 Redisson 分布式锁 charge:lock:{chargerId}
     * 3. 锁内二次校验充电桩状态（防 ABA）
     * 4. 更新充电桩状态为 CHARGING（Redis + MySQL）
     * 5. 创建 ChargeOrder（先充后付：orderStatus=CHARGING, payStatus=UNPAID）
     * 6. 通过 DeviceService 下发 MQTT START_CHARGE 指令（best-effort）
     * 7. 推送充电开始事件（WebSocket + RocketMQ）
     * 8. 返回订单编号
     * </p>
     *
     * @param userId    用户ID
     * @param chargerId 充电桩ID
     * @return 充电订单
     * @throws com.iot.common.exception.BusinessException 充电桩不可用时抛出
     */
    ChargeOrder startCharge(Long userId, Long chargerId);

    /**
     * 结束充电
     * <p>
     * 核心流程：
     * 1. 校验订单存在且状态为 CHARGING
     * 2. 通过 DeviceService 下发 MQTT STOP_CHARGE 指令
     * 3. 从充电桩读取最终充电数据
     * 4. 调用 PricingService 计算精确费用
     * 5. 更新订单状态为 COMPLETED，写入费用信息
     * 6. 更新充电桩状态为 IDLE
     * 7. 推送充电结束事件
     * </p>
     *
     * @param userId  用户ID
     * @param orderNo 订单编号
     * @return 更新后的充电订单
     */
    ChargeOrder stopCharge(Long userId, String orderNo);

    /**
     * 获取充电实时状态
     * <p>
     * 从订单表获取基本信息，从 Redis 获取实时电力数据，
     * 调用计费服务估算当前费用。
     * </p>
     *
     * @param orderNo 订单编号
     * @return 充电实时状态
     */
    ChargeStatusVO getChargeStatus(String orderNo);

    /**
     * 通过订单编号查找正在充电的订单
     *
     * @param orderNo 订单编号
     * @return 充电订单，不存在时返回 null
     */
    ChargeOrder getChargingOrder(String orderNo);
}
