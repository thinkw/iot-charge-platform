package com.iot.core.service;

import com.iot.core.entity.PricingRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计费服务接口
 * <p>
 * 提供电价查询、费用估算和精确计费功能。
 * 支持基础电价（ruleType=1）和峰谷电价（ruleType=2）。
 * 规则匹配优先级：station级（priority高的优先） → 全局规则（stationId=0）。
 * </p>
 *
 * @author IoT Team
 */
public interface PricingService {

    /**
     * 获取当前时间点有效的计费规则
     * <p>
     * 1. 先查找 stationId 匹配的规则（按 priority 降序）
     * 2. 对峰谷电价规则，校验当前时间是否在时段内
     * 3. 站级规则无匹配时 fallback 到全局规则（stationId=0）
     * </p>
     *
     * @param stationId 充电站ID
     * @param time      查询时间点
     * @return 有效的计费规则，找不到则返回 null
     */
    PricingRule getEffectivePricing(Long stationId, LocalDateTime time);

    /**
     * 估算充电费用（充电中实时展示用）
     * <p>
     * 取当前时段的电价进行简单估算，精度不要求极高。
     * fee = energy × (electricityPrice + servicePrice)
     * </p>
     *
     * @param stationId 充电站ID
     * @param energy    已充电量(kWh)
     * @return 估算费用（元）
     */
    BigDecimal estimateFee(Long stationId, BigDecimal energy);

    /**
     * 精确计算充电费用（充电结束后使用）
     * <p>
     * 考虑充电时段跨越多个计费规则时段的情况，分段计算后累加。
     * 优先使用设备上报的能量时间线数据进行增量计费，
     * 时间线数据不可用时 fallback 到均摊计费。
     * 返回值包含电费、服务费和总金额的明细。
     * </p>
     *
     * @param stationId   充电站ID
     * @param startTime   充电开始时间
     * @param endTime     充电结束时间
     * @param totalEnergy 总充电量(kWh)
     * @param sn          设备SN，用于查询能量时间线数据
     * @return 费用明细
     */
    FeeDetail calculateExactFee(Long stationId, LocalDateTime startTime,
                                LocalDateTime endTime, BigDecimal totalEnergy,
                                String sn);

    /**
     * 获取充电站的最低电价
     * <p>
     * 用于充电站列表页的价格展示和排序。
     * 先查站级规则，无结果时查全局规则。
     * </p>
     *
     * @param stationId 充电站ID
     * @return 最低电价（元/kWh），无规则时返回0
     */
    BigDecimal getMinPrice(Long stationId);

    /**
     * 费用明细内部类
     */
    record FeeDetail(BigDecimal electricityFee, BigDecimal serviceFee, BigDecimal totalAmount) {
        public FeeDetail {
            if (electricityFee == null) electricityFee = BigDecimal.ZERO;
            if (serviceFee == null) serviceFee = BigDecimal.ZERO;
            if (totalAmount == null) totalAmount = BigDecimal.ZERO;
        }
    }
}
