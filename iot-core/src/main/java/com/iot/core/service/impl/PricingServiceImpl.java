package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.core.entity.PricingRule;
import com.iot.core.mapper.PricingRuleMapper;
import com.iot.core.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * 计费服务实现类
 * <p>
 * 实现基础电价和峰谷电价的计算逻辑。
 * 规则匹配按 stationId + priority 降序查找，支持时段切分的精确计费。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final PricingRuleMapper pricingRuleMapper;

    /**
     * 规则类型常量
     */
    private static final int RULE_TYPE_BASIC = 1;
    private static final int RULE_TYPE_PEAK_VALLEY = 2;

    /**
     * 获取当前时间点有效的计费规则
     * <p>
     * 查询该站所有启用的规则，按 priority 降序排列。
     * 对于峰谷电价规则，额外校验当前时间是否在时段范围内。
     * 站级无匹配时回退到全局规则（stationId=0）。
     * </p>
     */
    @Override
    public PricingRule getEffectivePricing(Long stationId, LocalDateTime time) {
        // 1. 查询站级规则
        List<PricingRule> rules = pricingRuleMapper.selectList(
                new LambdaQueryWrapper<PricingRule>()
                        .eq(PricingRule::getStationId, stationId)
                        .eq(PricingRule::getStatus, 1)
                        .orderByDesc(PricingRule::getPriority)
        );

        // 2. 匹配站级规则
        PricingRule matched = matchRule(rules, time);
        if (matched != null) {
            return matched;
        }

        // 3. fallback 到全局规则
        List<PricingRule> globalRules = pricingRuleMapper.selectList(
                new LambdaQueryWrapper<PricingRule>()
                        .eq(PricingRule::getStationId, 0)
                        .eq(PricingRule::getStatus, 1)
                        .orderByDesc(PricingRule::getPriority)
        );

        return matchRule(globalRules, time);
    }

    /**
     * 从规则列表中匹配当前时间适用的规则
     * <p>
     * 基础电价（ruleType=1）：任意时间都匹配，取 priority 最高的
     * 峰谷电价（ruleType=2）：当前时间在 startTime-endTime 范围内才匹配
     * </p>
     */
    private PricingRule matchRule(List<PricingRule> rules, LocalDateTime time) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        LocalTime now = time.toLocalTime();

        for (PricingRule rule : rules) {
            if (rule.getRuleType() == RULE_TYPE_BASIC) {
                // 基础电价：直接匹配第一条（已按 priority 降序）
                return rule;
            }
            if (rule.getRuleType() == RULE_TYPE_PEAK_VALLEY
                    && rule.getStartTime() != null && rule.getEndTime() != null) {
                // 峰谷电价：校验当前时间是否在时段内
                if (isTimeInRange(now, rule.getStartTime(), rule.getEndTime())) {
                    return rule;
                }
            }
        }

        // 峰谷电价规则都不在时间段内时，找第一条基础电价规则
        return rules.stream()
                .filter(r -> r.getRuleType() == RULE_TYPE_BASIC)
                .findFirst()
                .orElse(null);
    }

    /**
     * 估算充电费用
     */
    @Override
    public BigDecimal estimateFee(Long stationId, BigDecimal energy) {
        if (energy == null || energy.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        PricingRule rule = getEffectivePricing(stationId, LocalDateTime.now());
        if (rule == null) {
            log.warn("[费用估算] 未找到有效的计费规则 - stationId: {}", stationId);
            return BigDecimal.ZERO;
        }

        BigDecimal unitPrice = rule.getElectricityPrice().add(rule.getServicePrice());
        return energy.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 精确计算充电费用
     * <p>
     * 将充电时间段按计费规则的时段进行切分，分段计算。
     * 简化实现：使用充电时间段中间时刻的规则进行计算。
     * 如果充电时间跨越多天或时段切分复杂，以中间时刻为准。
     * </p>
     */
    @Override
    public FeeDetail calculateExactFee(Long stationId, LocalDateTime startTime,
                                        LocalDateTime endTime, BigDecimal totalEnergy) {
        if (totalEnergy == null || totalEnergy.compareTo(BigDecimal.ZERO) <= 0) {
            return new FeeDetail(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // 充电持续时长（分钟）
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        if (durationMinutes <= 0) {
            durationMinutes = 1;
        }

        // 获取充电中间时刻的计费规则（简化处理）
        LocalDateTime midTime = startTime.plusMinutes(durationMinutes / 2);
        PricingRule rule = getEffectivePricing(stationId, midTime);

        if (rule == null) {
            log.warn("[精确计费] 未找到有效的计费规则 - stationId: {}", stationId);
            return new FeeDetail(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // 按每分钟为单位计算各时段费用
        // 简化：如果只有基础电价，直接按总用电量计算
        List<PricingRule> allRules = pricingRuleMapper.selectList(
                new LambdaQueryWrapper<PricingRule>()
                        .eq(PricingRule::getStationId, stationId)
                        .eq(PricingRule::getStatus, 1)
        );

        // 如果没有站级规则，使用全局规则
        if (allRules.isEmpty()) {
            allRules = pricingRuleMapper.selectList(
                    new LambdaQueryWrapper<PricingRule>()
                            .eq(PricingRule::getStationId, 0)
                            .eq(PricingRule::getStatus, 1)
            );
        }

        if (allRules.isEmpty() || allRules.stream().allMatch(r -> r.getRuleType() == RULE_TYPE_BASIC)) {
            // 纯基础电价：直接计算
            BigDecimal electricityFee = totalEnergy.multiply(rule.getElectricityPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal serviceFee = totalEnergy.multiply(rule.getServicePrice())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalAmount = electricityFee.add(serviceFee);
            return new FeeDetail(electricityFee, serviceFee, totalAmount);
        }

        // 峰谷电价：按分钟遍历切分（性能优化：超过一定时长则以中间时刻为准）
        if (durationMinutes > 1440) {
            // 超过24小时，以中间时刻规则为准简化处理
            BigDecimal electricityFee = totalEnergy.multiply(rule.getElectricityPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal serviceFee = totalEnergy.multiply(rule.getServicePrice())
                    .setScale(2, RoundingMode.HALF_UP);
            return new FeeDetail(electricityFee, serviceFee, electricityFee.add(serviceFee));
        }

        // 按分钟分段计算
        BigDecimal energyPerMinute = totalEnergy.divide(
                BigDecimal.valueOf(durationMinutes), 8, RoundingMode.HALF_UP);
        BigDecimal totalElectricityFee = BigDecimal.ZERO;
        BigDecimal totalServiceFee = BigDecimal.ZERO;

        LocalDateTime currentTime = startTime;
        while (currentTime.isBefore(endTime)) {
            PricingRule currentRule = getEffectivePricing(stationId, currentTime);
            if (currentRule != null) {
                totalElectricityFee = totalElectricityFee.add(
                        energyPerMinute.multiply(currentRule.getElectricityPrice()));
                totalServiceFee = totalServiceFee.add(
                        energyPerMinute.multiply(currentRule.getServicePrice()));
            }
            currentTime = currentTime.plusMinutes(1);
        }

        totalElectricityFee = totalElectricityFee.setScale(2, RoundingMode.HALF_UP);
        totalServiceFee = totalServiceFee.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = totalElectricityFee.add(totalServiceFee);

        log.info("[精确计费] stationId: {}, 时长: {}分钟, 电量: {}kWh, 电费: {}, 服务费: {}, 总费用: {}",
                stationId, durationMinutes, totalEnergy, totalElectricityFee, totalServiceFee, totalAmount);

        return new FeeDetail(totalElectricityFee, totalServiceFee, totalAmount);
    }

    /**
     * 获取充电站最低电价
     */
    @Override
    public BigDecimal getMinPrice(Long stationId) {
        List<PricingRule> rules = pricingRuleMapper.selectList(
                new LambdaQueryWrapper<PricingRule>()
                        .eq(PricingRule::getStationId, stationId)
                        .eq(PricingRule::getStatus, 1)
        );

        // 站级规则无结果时查全局
        if (rules.isEmpty()) {
            rules = pricingRuleMapper.selectList(
                    new LambdaQueryWrapper<PricingRule>()
                            .eq(PricingRule::getStationId, 0)
                            .eq(PricingRule::getStatus, 1)
            );
        }

        return rules.stream()
                .map(PricingRule::getElectricityPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 判断当前时间是否在指定的时间段内
     * <p>
     * 支持跨天时段（如 22:00-06:00 表示跨天）。
     * 不跨天：startTime < endTime，如 08:00-18:00
     * 跨天：startTime > endTime，如 22:00-06:00
     * </p>
     */
    private boolean isTimeInRange(LocalTime now, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            // 不跨天：08:00 - 18:00
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            // 跨天：22:00 - 06:00
            return !now.isBefore(start) || now.isBefore(end);
        }
    }
}
