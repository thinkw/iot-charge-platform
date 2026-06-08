package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.common.constant.DeviceConstants;
import com.iot.core.entity.PricingRule;
import com.iot.core.mapper.PricingRuleMapper;
import com.iot.core.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 计费服务实现类
 * <p>
 * 实现基础电价和峰谷电价的计算逻辑。
 * 规则匹配按 stationId + priority 降序查找，支持时段切分的精确计费。
 * 支持 Redis 能量时间线增量计费，以及多日超长充电的按天切段计算。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final PricingRuleMapper pricingRuleMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 规则内存缓存：key=stationId，value=该站+全局规则的合并列表
     * <p>
     * 计费规则变更频率极低，一次充电过程内缓存规则避免反复查 DB。
     * 运营后台修改规则后需调用 {@link #evictRulesCache(Long)} 刷新。
     * </p>
     */
    private final ConcurrentHashMap<Long, List<PricingRule>> rulesCache = new ConcurrentHashMap<>();

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
        // 使用缓存的预加载规则 + 纯内存匹配，避免每次查 DB
        List<PricingRule> allRules = loadAllRules(stationId);
        return matchRuleFromCache(allRules, time);
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
     * 精确计算充电费用（问题修复版）
     * <p>
     * 三处关键优化：
     * 1. 预加载所有计费规则到内存（一次 DB 查询），避免每分钟循环重复查 DB
     * 2. 优先使用 Redis ZSET 能量时间线获取每分钟增量电量，替代均摊计算
     * 3. 超过 24h 的充电按天拆分窗口分段计算，而非用单一中位时刻估算
     * </p>
     * <p>
     * 计费精度：按分钟切分，每段匹配对应的峰谷/基础电价。
     * 纯基础电价场景走快速路径，无需按分钟遍历。
     * </p>
     */
    @Override
    public FeeDetail calculateExactFee(Long stationId, LocalDateTime startTime,
                                        LocalDateTime endTime, BigDecimal totalEnergy,
                                        String sn) {
        if (totalEnergy == null || totalEnergy.compareTo(BigDecimal.ZERO) <= 0) {
            return new FeeDetail(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // 充电持续时长（分钟），向上取整，确保不满1分钟也按1分钟计费
        long durationMillis = Duration.between(startTime, endTime).toMillis();
        long durationMinutes = Math.max(1, (long) Math.ceil(durationMillis / 60000.0));

        // 预加载所有计费规则（一次查询，内存缓存复用）
        List<PricingRule> allRules = loadAllRules(stationId);
        if (allRules.isEmpty()) {
            log.warn("[精确计费] 未找到有效的计费规则 - stationId: {}", stationId);
            return new FeeDetail(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // 纯基础电价：直接快速计算（无需按分钟遍历）
        if (allRules.stream().allMatch(r -> r.getRuleType() == RULE_TYPE_BASIC)) {
            PricingRule rule = allRules.get(0);  // 按 priority 排序，取第一条
            BigDecimal electricityFee = totalEnergy.multiply(rule.getElectricityPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal serviceFee = totalEnergy.multiply(rule.getServicePrice())
                    .setScale(2, RoundingMode.HALF_UP);
            log.info("[精确计费] 纯基础电价 - stationId: {}, 时长: {}分钟, 电量: {}kWh, 电费: {}, 服务费: {}, 总费用: {}",
                    stationId, durationMinutes, totalEnergy, electricityFee, serviceFee,
                    electricityFee.add(serviceFee));
            return new FeeDetail(electricityFee, serviceFee, electricityFee.add(serviceFee));
        }

        // 超过 24 小时：按天拆分窗口分段计算
        if (durationMinutes > 1440) {
            log.info("[精确计费] 充电时长超过24小时({}分钟)，按天拆分计算", durationMinutes);
            return calculateMultiDayFee(stationId, sn, startTime, endTime, totalEnergy, allRules);
        }

        // 峰谷电价 + ≤24h：按分钟精度计算
        return calculatePerMinuteFee(stationId, sn, startTime, endTime, totalEnergy, allRules);
    }

    // ==================== 新增私有方法 ====================

    /**
     * 一次性加载充电站 + 全局的所有启用规则（带内存缓存）
     * <p>
     * 按 priority 降序排列，确保高优先级规则先匹配。
     * 站级规则和全局规则合并为一个列表返回。
     * 首次查询后缓存在内存中，后续充电过程直接复用。
     * </p>
     *
     * @param stationId 充电站ID
     * @return 合并后的规则列表
     */
    private List<PricingRule> loadAllRules(Long stationId) {
        // 先从内存缓存取，命中直接返回
        List<PricingRule> cached = rulesCache.get(stationId);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，查 DB 并构建规则列表（用 ArrayList 包装以支持后续 addAll）
        List<PricingRule> rules = new java.util.ArrayList<>(pricingRuleMapper.selectList(
                new LambdaQueryWrapper<PricingRule>()
                        .eq(PricingRule::getStationId, stationId)
                        .eq(PricingRule::getStatus, 1)
                        .orderByDesc(PricingRule::getPriority)
        ));

        // 站级规则不足时追加全局规则（stationId=0）
        if (rules.isEmpty()) {
            rules = pricingRuleMapper.selectList(
                    new LambdaQueryWrapper<PricingRule>()
                            .eq(PricingRule::getStationId, 0)
                            .eq(PricingRule::getStatus, 1)
                            .orderByDesc(PricingRule::getPriority)
            );
        } else {
            // 如果站级已有基础电价规则，额外加载全局的峰谷规则作为补充
            List<PricingRule> globalRules = pricingRuleMapper.selectList(
                    new LambdaQueryWrapper<PricingRule>()
                            .eq(PricingRule::getStationId, 0)
                            .eq(PricingRule::getStatus, 1)
                            .eq(PricingRule::getRuleType, RULE_TYPE_PEAK_VALLEY)
                            .orderByDesc(PricingRule::getPriority)
            );
            if (!globalRules.isEmpty()) {
                rules.addAll(globalRules);
            }
            // 如果站级没有基础电价，也从全局加载
            if (rules.stream().noneMatch(r -> r.getRuleType() == RULE_TYPE_BASIC)) {
                List<PricingRule> globalBasic = pricingRuleMapper.selectList(
                        new LambdaQueryWrapper<PricingRule>()
                                .eq(PricingRule::getStationId, 0)
                                .eq(PricingRule::getStatus, 1)
                                .eq(PricingRule::getRuleType, RULE_TYPE_BASIC)
                );
                rules.addAll(globalBasic);
            }
        }

        // 写入缓存
        rulesCache.put(stationId, rules);
        log.debug("[规则缓存] stationId: {} 的计费规则已加载并缓存（{}条）", stationId, rules.size());
        return rules;
    }

    /**
     * 清除指定充电站的规则缓存
     * <p>
     * 运营后台修改计费规则后调用，确保下次查询获取最新规则。
     * stationId 为 null 时清除全部缓存。
     * </p>
     *
     * @param stationId 充电站ID，null 表示清除全部
     */
    public void evictRulesCache(Long stationId) {
        if (stationId == null) {
            rulesCache.clear();
            log.info("[规则缓存] 全部缓存已清除");
        } else {
            rulesCache.remove(stationId);
            log.info("[规则缓存] stationId: {} 的缓存已清除", stationId);
        }
    }

    /**
     * 从预加载的规则列表中匹配指定时间的有效规则（纯内存操作，不查 DB）
     * <p>
     * 匹配逻辑与 {@link #matchRule(List, LocalDateTime)} 一致，但入参为已加载的规则列表。
     * 优先匹配峰谷电价规则（ruleType=2），无匹配时 fallback 到基础电价规则（ruleType=1）。
     * </p>
     *
     * @param rules 已预加载的规则列表
     * @param time  查询时间点
     * @return 匹配的规则，无匹配则返回 null
     */
    private PricingRule matchRuleFromCache(List<PricingRule> rules, LocalDateTime time) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        LocalTime now = time.toLocalTime();

        // 先找匹配的峰谷电价规则（按 priority 降序，第一条匹配即返回）
        for (PricingRule rule : rules) {
            if (rule.getRuleType() == RULE_TYPE_PEAK_VALLEY
                    && rule.getStartTime() != null && rule.getEndTime() != null) {
                if (isTimeInRange(now, rule.getStartTime(), rule.getEndTime())) {
                    return rule;
                }
            }
        }
        // 峰谷不匹配，返回第一条基础电价规则
        for (PricingRule rule : rules) {
            if (rule.getRuleType() == RULE_TYPE_BASIC) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 从 Redis ZSET 获取每分钟的实际增量电量
     * <p>
     * 从设备能量时间线中查询充电时间段内的数据点，对相邻数据点做差分得到每分钟增量。
     * 如果时间线数据不足（设备上报间隔不等或缺失），则使用线性插值补充。
     * 数据完全不可用时返回空 Map，调用方应 fallback 到均摊计算。
     * </p>
     *
     * @param sn        设备SN
     * @param startTime 充电开始时间
     * @param endTime   充电结束时间
     * @return Map<分钟时刻, 该分钟电量增量(kWh)>，按时间排序
     */
    private Map<LocalDateTime, BigDecimal> getPerMinuteEnergy(String sn,
                                                               LocalDateTime startTime,
                                                               LocalDateTime endTime) {
        Map<LocalDateTime, BigDecimal> result = new LinkedHashMap<>();
        if (sn == null) {
            return result;
        }

        try {
            String timelineKey = DeviceConstants.REDIS_KEY_ENERGY_TIMELINE + sn;
            // 向前多取 60 秒数据，确保能找到充电开始时刻的正确基线累计电量
            long startEpoch = startTime.minusSeconds(60)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endEpoch = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            // 从 ZSET 查询时间范围内的所有数据点
            Set<ZSetOperations.TypedTuple<Object>> entries = redisTemplate.opsForZSet()
                    .rangeByScoreWithScores(timelineKey, startEpoch, endEpoch);

            if (entries == null || entries.size() < 2) {
                log.debug("[能量时间线] SN: {} 时间范围内数据点不足({})，使用均摊 fallback",
                        sn, entries == null ? 0 : entries.size());
                return result;
            }

            // 解析数据点：(时间戳, 累计电量)
            // ZSET member 格式为 "timestamp:energyValue"，需拆分后取 energy 部分
            List<Map.Entry<Long, Double>> dataPoints = entries.stream()
                    .filter(t -> t.getValue() != null && t.getScore() != null)
                    .map(t -> {
                        String memberStr = t.getValue().toString();
                        int colonIdx = memberStr.lastIndexOf(':');
                        double energyVal = Double.parseDouble(
                                colonIdx > 0 ? memberStr.substring(colonIdx + 1) : memberStr);
                        return Map.entry(t.getScore().longValue(), energyVal);
                    })
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toList());

            if (dataPoints.size() < 2) {
                return result;
            }

            // 对每分钟做电量累积差分计算
            // 核心思路：每整分钟取该分钟起止时刻的累计电量差值作为该分钟实际消耗
            LocalDateTime minuteStart = startTime.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            LocalDateTime minuteEnd = endTime.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            // 确保不满1分钟的充电至少覆盖1个分钟窗口
            if (minuteEnd.isBefore(minuteStart)) {
                minuteEnd = minuteStart;
            }
            ZoneId zone = ZoneId.systemDefault();

            int dpIdx = 0;
            // 找到充电开始时刻的基准累计电量（≤startTime 的最后一个数据点）
            long startMs = startTime.atZone(zone).toInstant().toEpochMilli();
            double baselineEnergy = dataPoints.get(0).getValue();
            while (dpIdx < dataPoints.size() && dataPoints.get(dpIdx).getKey() <= startMs) {
                baselineEnergy = dataPoints.get(dpIdx).getValue();
                dpIdx++;
            }

            LocalDateTime cursor = minuteStart;
            while (!cursor.isAfter(minuteEnd)) {
                long minuteEndMs = cursor.plusMinutes(1).atZone(zone).toInstant().toEpochMilli();

                // 找到该分钟结束时刻的累计电量（≤minuteEnd 的最后一个数据点）
                double endEnergy = baselineEnergy;
                while (dpIdx < dataPoints.size()
                        && dataPoints.get(dpIdx).getKey() <= minuteEndMs) {
                    endEnergy = dataPoints.get(dpIdx).getValue();
                    dpIdx++;
                }

                // 该分钟的增量电量 = 结束累计 - 开始累计
                BigDecimal minuteEnergy = BigDecimal.valueOf(Math.max(0, endEnergy - baselineEnergy))
                        .setScale(8, RoundingMode.HALF_UP);

                // 防止数据异常导致单分钟能量远超合理值（快充桩峰值约 120kW → 每分钟 2kWh）
                if (minuteEnergy.compareTo(BigDecimal.valueOf(5)) > 0) {
                    minuteEnergy = BigDecimal.ZERO;
                }

                result.put(cursor, minuteEnergy);

                // 下一分钟的基准 = 当前分钟的结束累计
                baselineEnergy = endEnergy;
                cursor = cursor.plusMinutes(1);
            }

            log.info("[能量时间线] SN: {}, 充电时段: {} ~ {}, 数据点: {}个, "
                            + "分钟窗口: {} ~ {}, 分钟数据: {}个",
                    sn, startTime, endTime, dataPoints.size(),
                    minuteStart, minuteEnd, result.size());
            if (!result.isEmpty()) {
                log.info("[能量时间线] SN: {}, 分钟能量总和: {} kWh (总电量: {} kWh)",
                        sn,
                        result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add),
                        BigDecimal.valueOf(dataPoints.get(dataPoints.size() - 1).getValue()
                                - dataPoints.get(0).getValue()));
            }
        } catch (Exception e) {
            log.warn("[能量时间线] 查询失败 - SN: {}, error: {}", sn, e.getMessage());
            return new LinkedHashMap<>();  // 返回空 Map 触发均摊 fallback
        }

        return result;
    }

    /**
     * 按分钟精度计算峰谷电价费用
     * <p>
     * 优先从 Redis 能量时间线获取每分钟的实际增量电量；
     * 时间线不可用时 fallback 到均摊（总电量 / 总分钟数）。
     * 每段分钟用内存匹配到的电价规则计算。
     * </p>
     *
     * @param stationId   充电站ID
     * @param sn          设备SN
     * @param startTime   充电开始时间
     * @param endTime     充电结束时间
     * @param totalEnergy 总充电量
     * @param allRules    预加载的规则列表
     * @return 费用明细
     */
    private FeeDetail calculatePerMinuteFee(Long stationId, String sn,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             BigDecimal totalEnergy,
                                             List<PricingRule> allRules) {
        // 时长向上取整，不满1分钟按1分钟计
        long durationMillis = Duration.between(startTime, endTime).toMillis();
        long durationMinutes = Math.max(1, (long) Math.ceil(durationMillis / 60000.0));

        // 尝试获取每分钟的实际增量电量
        Map<LocalDateTime, BigDecimal> perMinuteEnergy = getPerMinuteEnergy(sn, startTime, endTime);
        boolean useIncremental = !perMinuteEnergy.isEmpty();

        // 增量模式下归一化：确保各分钟增量之和 = totalEnergy，防止时间线对齐偏差导致电量丢失
        if (useIncremental) {
            BigDecimal sum = perMinuteEnergy.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal scale = totalEnergy.divide(sum, 8, RoundingMode.HALF_UP);
                perMinuteEnergy.replaceAll((k, v) -> v.multiply(scale).setScale(8, RoundingMode.HALF_UP));
            } else {
                // 增量全为 0（数据异常），回退到均摊模式
                log.warn("[精确计费] 增量能量全为0，回退到均摊模式 - SN: {}", sn);
                useIncremental = false;
            }
        }

        // 均摊 fallback 电量
        BigDecimal energyPerMinute = totalEnergy.divide(
                BigDecimal.valueOf(durationMinutes), 8, RoundingMode.HALF_UP);

        BigDecimal totalElectricityFee = BigDecimal.ZERO;
        BigDecimal totalServiceFee = BigDecimal.ZERO;

        LocalDateTime currentTime = startTime;
        while (currentTime.isBefore(endTime)) {
            // 纯内存匹配，不查 DB
            PricingRule currentRule = matchRuleFromCache(allRules, currentTime);
            if (currentRule != null) {
                // 优先用增量电量，fallback 到均摊电量
                BigDecimal minuteEnergy;
                if (useIncremental) {
                    LocalDateTime minuteKey = currentTime.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
                    minuteEnergy = perMinuteEnergy.getOrDefault(minuteKey, energyPerMinute);
                } else {
                    minuteEnergy = energyPerMinute;
                }
                totalElectricityFee = totalElectricityFee.add(
                        minuteEnergy.multiply(currentRule.getElectricityPrice()));
                totalServiceFee = totalServiceFee.add(
                        minuteEnergy.multiply(currentRule.getServicePrice()));
            }
            currentTime = currentTime.plusMinutes(1);
        }

        totalElectricityFee = totalElectricityFee.setScale(2, RoundingMode.HALF_UP);
        totalServiceFee = totalServiceFee.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = totalElectricityFee.add(totalServiceFee);

        // 最低收费保障：有电量消耗时费用不得为 0（防止短时充电免费蹭电）
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0
                && totalEnergy.compareTo(BigDecimal.ZERO) > 0) {
            // 取任意一条规则的单价计算最低费用，最少收 0.01 元
            PricingRule anyRule = allRules.get(0);
            BigDecimal minFee = totalEnergy.multiply(
                    anyRule.getElectricityPrice().add(anyRule.getServicePrice()))
                    .setScale(2, RoundingMode.HALF_UP);
            if (minFee.compareTo(BigDecimal.ZERO) == 0) {
                minFee = new BigDecimal("0.01");
            }
            totalElectricityFee = minFee.multiply(anyRule.getElectricityPrice())
                    .divide(anyRule.getElectricityPrice().add(anyRule.getServicePrice()),
                            2, RoundingMode.HALF_UP);
            totalServiceFee = minFee.subtract(totalElectricityFee);
            totalAmount = minFee;
            log.warn("[精确计费] 触发最低收费保障 - stationId: {}, SN: {}, 电量: {}kWh, 最低费: {}",
                    stationId, sn, totalEnergy, minFee);
        }

        log.info("[精确计费] stationId: {}, SN: {}, 时长: {}分钟, 电量: {}kWh, "
                        + "增量模式: {}, 电费: {}, 服务费: {}, 总费用: {}",
                stationId, sn, durationMinutes, totalEnergy, useIncremental,
                totalElectricityFee, totalServiceFee, totalAmount);

        return new FeeDetail(totalElectricityFee, totalServiceFee, totalAmount);
    }

    /**
     * 多日充电分段计费（充电时长 > 24h）
     * <p>
     * 将充电时间按 24h 窗口切分，每个窗口独立计算费用后累加。
     * 每个窗口使用该窗口的中位时刻取价，实际充电极少超 24h，
     * 此处作为兜底健壮性处理。
     * </p>
     *
     * @param stationId   充电站ID
     * @param sn          设备SN
     * @param startTime   充电开始时间
     * @param endTime     充电结束时间
     * @param totalEnergy 总充电量
     * @param allRules    预加载的规则列表
     * @return 费用明细
     */
    private FeeDetail calculateMultiDayFee(Long stationId, String sn,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            BigDecimal totalEnergy,
                                            List<PricingRule> allRules) {
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        int fullDays = (int) (durationMinutes / 1440);
        int remainingMinutes = (int) (durationMinutes % 1440);

        BigDecimal totalElectricityFee = BigDecimal.ZERO;
        BigDecimal totalServiceFee = BigDecimal.ZERO;

        // 每满 24h 为一个窗口
        for (int day = 0; day < fullDays; day++) {
            LocalDateTime dayStart = startTime.plusDays(day);
            LocalDateTime dayEnd = dayStart.plusDays(1);
            BigDecimal dayEnergy = totalEnergy.multiply(BigDecimal.valueOf(1440))
                    .divide(BigDecimal.valueOf(durationMinutes), 8, RoundingMode.HALF_UP);
            FeeDetail dayFee = calculatePerMinuteFee(stationId, sn, dayStart, dayEnd, dayEnergy, allRules);
            totalElectricityFee = totalElectricityFee.add(dayFee.electricityFee());
            totalServiceFee = totalServiceFee.add(dayFee.serviceFee());
        }

        // 最后不足一天的部分
        if (remainingMinutes > 0) {
            LocalDateTime remainderStart = startTime.plusDays(fullDays);
            BigDecimal remainderEnergy = totalEnergy.multiply(BigDecimal.valueOf(remainingMinutes))
                    .divide(BigDecimal.valueOf(durationMinutes), 8, RoundingMode.HALF_UP);
            FeeDetail remainderFee = calculatePerMinuteFee(stationId, sn, remainderStart, endTime,
                    remainderEnergy, allRules);
            totalElectricityFee = totalElectricityFee.add(remainderFee.electricityFee());
            totalServiceFee = totalServiceFee.add(remainderFee.serviceFee());
        }

        BigDecimal finalElectricity = totalElectricityFee.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalService = totalServiceFee.setScale(2, RoundingMode.HALF_UP);

        log.info("[精确计费-多日] stationId: {}, SN: {}, 总时长: {}分钟({}天+{}分钟), "
                        + "电量: {}kWh, 电费: {}, 服务费: {}, 总费用: {}",
                stationId, sn, durationMinutes, fullDays, remainingMinutes,
                totalEnergy, finalElectricity, finalService, finalElectricity.add(finalService));

        return new FeeDetail(finalElectricity, finalService, finalElectricity.add(finalService));
    }

    /**
     * 获取充电站最低电价
     * <p>
     * 复用规则缓存，无需重复查 DB。
     * </p>
     */
    @Override
    public BigDecimal getMinPrice(Long stationId) {
        List<PricingRule> rules = loadAllRules(stationId);

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
