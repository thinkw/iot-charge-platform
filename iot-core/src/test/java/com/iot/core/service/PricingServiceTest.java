package com.iot.core.service;

import com.iot.core.entity.PricingRule;
import com.iot.core.mapper.PricingRuleMapper;
import com.iot.core.service.PricingService.FeeDetail;
import com.iot.core.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PricingServiceImpl 单元测试
 * <p>
 * 测试计费服务的核心逻辑：
 * - 基础电价/峰谷电价规则匹配
 * - 站级→全局规则回退
 * - 跨天时段判断
 * - 费用估算与精确计算
 * - 最低电价查询
 * </p>
 *
 * @author IoT Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PricingService 单元测试")
class PricingServiceTest {

    @Mock
    private PricingRuleMapper pricingRuleMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private PricingServiceImpl pricingService;

    private static final Long STATION_ID = 1L;
    private static final String TEST_SN = "CHG000001";

    /**
     * 每个测试前清除规则缓存，避免测试间缓存污染
     */
    @BeforeEach
    void clearCache() {
        pricingService.evictRulesCache(null);
    }

    // ==================== 测试数据工厂方法 ====================

    /** 创建基础电价规则（ruleType=1） */
    private PricingRule createBasicRule(Long stationId, BigDecimal elecPrice,
                                        BigDecimal servicePrice, int priority) {
        PricingRule rule = new PricingRule();
        rule.setStationId(stationId);
        rule.setRuleType(1);
        rule.setElectricityPrice(elecPrice);
        rule.setServicePrice(servicePrice);
        rule.setPriority(priority);
        rule.setStatus(1);
        rule.setName("基础电价-" + stationId);
        return rule;
    }

    /** 创建峰谷电价规则（ruleType=2），含时段范围 */
    private PricingRule createPeakValleyRule(Long stationId, LocalTime start, LocalTime end,
                                             BigDecimal elecPrice, BigDecimal servicePrice, int priority) {
        PricingRule rule = new PricingRule();
        rule.setStationId(stationId);
        rule.setRuleType(2);
        rule.setStartTime(start);
        rule.setEndTime(end);
        rule.setElectricityPrice(elecPrice);
        rule.setServicePrice(servicePrice);
        rule.setPriority(priority);
        rule.setStatus(1);
        rule.setName("峰谷电价-" + stationId);
        return rule;
    }

    /**
     * 配置 loadAllRules 的 mock 返回值序列
     * <p>
     * loadAllRules 的查询顺序：
     * 1. selectList(stationId) — 站级规则
     * 2. 若站级非空 → selectList(stationId=0, ruleType=2) — 全局峰谷
     * 3. 若站级无基础电价 → selectList(stationId=0, ruleType=1) — 全局基础
     * </p>
     *
     * @param stationRules      站级规则列表
     * @param globalPeakValley  全局峰谷规则列表
     * @param needGlobalBasic   是否需要全局基础电价
     * @param globalBasic       全局基础规则列表
     */
    @SuppressWarnings("unchecked")
    private void mockLoadAllRules(List<PricingRule> stationRules,
                                   List<PricingRule> globalPeakValley,
                                   boolean needGlobalBasic,
                                   List<PricingRule> globalBasic) {
        if (stationRules.isEmpty()) {
            // 站级无规则 → 只查全局全部规则（一次调用）
            when(pricingRuleMapper.selectList(any()))
                    .thenReturn(globalBasic); // 全局全部规则作为 fallback
        } else {
            // 站级有规则 → 先查站级，再查全局峰谷，可能再查全局基础
            when(pricingRuleMapper.selectList(any()))
                    .thenReturn(stationRules)
                    .thenReturn(globalPeakValley != null ? globalPeakValley : Collections.emptyList());
            if (needGlobalBasic) {
                when(pricingRuleMapper.selectList(any()))
                        .thenReturn(stationRules)
                        .thenReturn(globalPeakValley != null ? globalPeakValley : Collections.emptyList())
                        .thenReturn(globalBasic != null ? globalBasic : Collections.emptyList());
            }
        }
    }

    // ==================== getEffectivePricing 测试 ====================

    @Test
    @DisplayName("getEffectivePricing - 站级规则匹配（基础电价）→ 返回站级规则")
    void getEffectivePricing_StationBasicRule_ReturnsStationRule() {
        /*
         * 测试场景：站级配置了基础电价规则
         * 预期：直接匹配并返回该站的基础电价规则
         */
        // Arrange
        PricingRule stationRule = createBasicRule(STATION_ID,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 10);
        // loadAllRules: 站级→[rule], 全局峰谷→[], 站级已有basic→不再查全局basic
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(stationRule))
                .thenReturn(Collections.emptyList());

        // Act
        PricingRule result = pricingService.getEffectivePricing(STATION_ID, LocalDateTime.now());

        // Assert
        assertNotNull(result, "应返回有效的计费规则");
        assertEquals(STATION_ID, result.getStationId(), "应返回站级规则");
        assertEquals(1, result.getRuleType(), "应为基础电价规则");
    }

    @Test
    @DisplayName("getEffectivePricing - 站级基础电价规则优先 → 按priority降序取第一条")
    void getEffectivePricing_StationMultipleBasicRules_ReturnsHighestPriority() {
        /*
         * 测试场景：站级配置了多条基础电价规则，优先级不同
         * 预期：返回 priority 最高的规则（priority=20）
         */
        // Arrange
        PricingRule lowPriority = createBasicRule(STATION_ID,
                new BigDecimal("1.0000"), new BigDecimal("0.3000"), 5);
        PricingRule midPriority = createBasicRule(STATION_ID,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 10);
        PricingRule highPriority = createBasicRule(STATION_ID,
                new BigDecimal("0.6000"), new BigDecimal("0.1000"), 20);
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(highPriority, midPriority, lowPriority))
                .thenReturn(Collections.emptyList());

        // Act
        PricingRule result = pricingService.getEffectivePricing(STATION_ID, LocalDateTime.now());

        // Assert
        assertNotNull(result, "应返回有效的计费规则");
        assertEquals(20, result.getPriority(), "应返回优先级最高的规则");
        assertEquals(0, new BigDecimal("0.6000").compareTo(result.getElectricityPrice()),
                "应返回最高优先级对应的电价");
    }

    @Test
    @DisplayName("getEffectivePricing - 峰谷电价，当前时间在时段内 → 返回峰谷规则")
    void getEffectivePricing_PeakValleyInRange_ReturnsPeakValleyRule() {
        /*
         * 测试场景：站级有峰谷电价规则（08:00-18:00），当前时间 12:00 在时段内
         * 预期：返回峰谷电价规则（ruleType=2）
         */
        // Arrange
        PricingRule peakValley = createPeakValleyRule(STATION_ID,
                LocalTime.of(8, 0), LocalTime.of(18, 0),
                new BigDecimal("1.2000"), new BigDecimal("0.3000"), 10);
        LocalDateTime currentTime = LocalDateTime.of(2024, 6, 1, 12, 0);
        // loadAllRules: 站级→[peakValley], 全局峰谷→[], 站级无basic→查全局basic→[]
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(peakValley))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());

        // Act
        PricingRule result = pricingService.getEffectivePricing(STATION_ID, currentTime);

        // Assert
        assertNotNull(result, "应返回有效的计费规则");
        assertEquals(2, result.getRuleType(), "应为峰谷电价规则");
        assertEquals(0, new BigDecimal("1.2000").compareTo(result.getElectricityPrice()),
                "应返回峰谷时段电价");
    }

    @Test
    @DisplayName("getEffectivePricing - 峰谷电价，当前时间不在时段内 → fallback到基础电价")
    void getEffectivePricing_PeakValleyOutOfRange_FallbackToBasic() {
        /*
         * 测试场景：站级有峰谷电价规则（08:00-18:00），当前时间 20:00 不在时段内
         * 预期：峰谷规则不匹配，应 fallback 到基础电价规则
         */
        // Arrange
        PricingRule peakValley = createPeakValleyRule(STATION_ID,
                LocalTime.of(8, 0), LocalTime.of(18, 0),
                new BigDecimal("1.2000"), new BigDecimal("0.3000"), 15);
        PricingRule basic = createBasicRule(STATION_ID,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 10);
        LocalDateTime currentTime = LocalDateTime.of(2024, 6, 1, 20, 0);
        // loadAllRules: 站级→[peakValley, basic], 全局峰谷→[], 站级有basic→不再查全局basic
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(peakValley, basic))
                .thenReturn(Collections.emptyList());

        // Act
        PricingRule result = pricingService.getEffectivePricing(STATION_ID, currentTime);

        // Assert
        assertNotNull(result, "应返回有效的计费规则");
        assertEquals(1, result.getRuleType(), "峰谷不匹配时应 fallback 为基础电价");
        assertEquals(0, new BigDecimal("0.8000").compareTo(result.getElectricityPrice()),
                "应返回基础电价");
    }

    @Test
    @DisplayName("getEffectivePricing - 站级无规则 → fallback到全局规则(stationId=0)")
    void getEffectivePricing_NoStationRule_FallbackToGlobal() {
        /*
         * 测试场景：站级无规则，全局有基础电价规则
         * 预期：返回全局规则（stationId=0）
         */
        // Arrange
        PricingRule globalRule = createBasicRule(0L,
                new BigDecimal("0.9000"), new BigDecimal("0.1500"), 5);
        // loadAllRules: 站级→[]，走全局全部规则→[globalRule]
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(globalRule));

        // Act
        PricingRule result = pricingService.getEffectivePricing(STATION_ID, LocalDateTime.now());

        // Assert
        assertNotNull(result, "站级无规则时应返回全局规则");
        assertEquals(0L, result.getStationId(), "应返回全局规则（stationId=0）");
        assertEquals(1, result.getRuleType(), "全局规则应为基础电价");
    }

    @Test
    @DisplayName("getEffectivePricing - 跨天时段（22:00-06:00）→ 在时段内应匹配")
    void getEffectivePricing_CrossDayInRange_MatchesPeakValley() {
        /*
         * 测试场景：峰谷电价跨天时段 22:00-06:00，当前时间 23:00 在跨天时段内
         * 预期：应匹配峰谷电价规则
         */
        // Arrange
        PricingRule peakValley = createPeakValleyRule(STATION_ID,
                LocalTime.of(22, 0), LocalTime.of(6, 0),
                new BigDecimal("0.5000"), new BigDecimal("0.1000"), 10);
        LocalDateTime nightTime = LocalDateTime.of(2024, 6, 1, 23, 0);
        // loadAllRules: 站级→[peakValley], 全局峰谷→[], 站级无basic→查全局basic→[]
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(peakValley))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());

        // Act
        PricingRule result = pricingService.getEffectivePricing(STATION_ID, nightTime);

        // Assert
        assertNotNull(result, "跨天时段内应匹配规则");
        assertEquals(2, result.getRuleType(), "应匹配峰谷电价规则");
        assertEquals(0, new BigDecimal("0.5000").compareTo(result.getElectricityPrice()),
                "应返回谷时电价");
    }

    @Test
    @DisplayName("getEffectivePricing - 跨天时段，不在时段内 → 不匹配")
    void getEffectivePricing_CrossDayOutOfRange_NotMatch() {
        /*
         * 测试场景：峰谷电价跨天时段 22:00-06:00，当前时间 12:00（中午）不在跨天时段内
         * 预期：峰谷规则不匹配，fallback 到基础电价
         */
        // Arrange
        PricingRule peakValley = createPeakValleyRule(STATION_ID,
                LocalTime.of(22, 0), LocalTime.of(6, 0),
                new BigDecimal("0.5000"), new BigDecimal("0.1000"), 10);
        PricingRule basic = createBasicRule(STATION_ID,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 5);
        LocalDateTime noonTime = LocalDateTime.of(2024, 6, 1, 12, 0);
        // loadAllRules: 站级→[peakValley, basic], 全局峰谷→[], 站级有basic→不查全局basic
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(peakValley, basic))
                .thenReturn(Collections.emptyList());

        // Act
        PricingRule result = pricingService.getEffectivePricing(STATION_ID, noonTime);

        // Assert
        assertNotNull(result, "应返回有效的计费规则");
        assertEquals(1, result.getRuleType(), "跨天时段外应 fallback 为基础电价");
        assertEquals(0, new BigDecimal("0.8000").compareTo(result.getElectricityPrice()),
                "应返回基础电价");
    }

    // ==================== estimateFee 测试 ====================

    @Test
    @DisplayName("estimateFee - 正常估算 → energy × (electricityPrice + servicePrice)")
    void estimateFee_Normal_CalculatesCorrectly() {
        /*
         * 测试场景：站级有基础电价规则，正常估算费用
         * 预期：estimateFee = energy × (electricityPrice + servicePrice)
         *       50 × (0.8000 + 0.2000) = 50.00
         */
        // Arrange
        PricingRule rule = createBasicRule(STATION_ID,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 10);
        // loadAllRules: 站级→[rule], 全局峰谷→[]
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(rule))
                .thenReturn(Collections.emptyList());

        // Act
        BigDecimal result = pricingService.estimateFee(STATION_ID, new BigDecimal("50"));

        // Assert
        assertEquals(0, result.compareTo(new BigDecimal("50.00")),
                "50 × (0.8 + 0.2) 应等于 50.00");
    }

    @Test
    @DisplayName("estimateFee - energy=0 → 返回 BigDecimal.ZERO")
    void estimateFee_ZeroEnergy_ReturnsZero() {
        /*
         * 测试场景：充电量为 0
         * 预期：直接返回 BigDecimal.ZERO，不需要查询计费规则
         */
        // Act
        BigDecimal result = pricingService.estimateFee(STATION_ID, BigDecimal.ZERO);

        // Assert
        assertEquals(BigDecimal.ZERO, result, "充电量为 0 时应返回 0");
        verifyNoInteractions(pricingRuleMapper);
    }

    @Test
    @DisplayName("estimateFee - 无有效规则 → 返回 BigDecimal.ZERO")
    void estimateFee_NoValidRule_ReturnsZero() {
        /*
         * 测试场景：站级和全局均无有效计费规则
         * 预期：返回 BigDecimal.ZERO
         */
        // Arrange
        // loadAllRules: 站级→[], 全局全部→[]
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());

        // Act
        BigDecimal result = pricingService.estimateFee(STATION_ID, new BigDecimal("50"));

        // Assert
        assertEquals(BigDecimal.ZERO, result, "无有效规则时应返回 0");
    }

    // ==================== getMinPrice 测试 ====================

    @Test
    @DisplayName("getMinPrice - 站级有多条规则 → 返回最低电价")
    void getMinPrice_MultipleStationRules_ReturnsMinPrice() {
        /*
         * 测试场景：站级有 3 条规则，电价分别为 0.8000、0.6000、1.0000
         * 预期：返回最低电价 0.6000
         */
        // Arrange
        PricingRule rule1 = createBasicRule(STATION_ID,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 10);
        PricingRule rule2 = createBasicRule(STATION_ID,
                new BigDecimal("0.6000"), new BigDecimal("0.1500"), 5);
        PricingRule rule3 = createBasicRule(STATION_ID,
                new BigDecimal("1.0000"), new BigDecimal("0.3000"), 20);
        // loadAllRules: 站级→[r1,r2,r3], 全局峰谷→[]
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(rule1, rule2, rule3))
                .thenReturn(Collections.emptyList());

        // Act
        BigDecimal result = pricingService.getMinPrice(STATION_ID);

        // Assert
        assertEquals(0, result.compareTo(new BigDecimal("0.6000")),
                "应返回最低电价 0.6000");
    }

    @Test
    @DisplayName("getMinPrice - 站级无规则 → 返回全局最低电价")
    void getMinPrice_NoStationRule_ReturnsGlobalMinPrice() {
        /*
         * 测试场景：站级无规则，全局有 3 条规则
         * 预期：返回全局最低电价 0.7000
         */
        // Arrange
        PricingRule globalRule1 = createBasicRule(0L,
                new BigDecimal("0.9000"), new BigDecimal("0.2000"), 10);
        PricingRule globalRule2 = createBasicRule(0L,
                new BigDecimal("0.7000"), new BigDecimal("0.1500"), 5);
        PricingRule globalRule3 = createBasicRule(0L,
                new BigDecimal("1.2000"), new BigDecimal("0.3000"), 20);
        // loadAllRules: 站级→[], 全局全部→[g1,g2,g3]
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(globalRule1, globalRule2, globalRule3));

        // Act
        BigDecimal result = pricingService.getMinPrice(STATION_ID);

        // Assert
        assertEquals(0, result.compareTo(new BigDecimal("0.7000")),
                "站级无规则时应返回全局最低电价 0.7000");
    }

    // ==================== calculateExactFee 测试 ====================

    @Test
    @DisplayName("calculateExactFee - 基础电价（简单计算）→ 正确的电费+服务费+总金额")
    void calculateExactFee_BasicPricing_ReturnsCorrectFeeDetail() {
        /*
         * 测试场景：纯基础电价，充电 100kWh
         * 预期：
         *   - electricityFee = 100 × 0.8000 = 80.00
         *   - serviceFee     = 100 × 0.2000 = 20.00
         *   - totalAmount    = 80.00 + 20.00 = 100.00
         */
        // Arrange
        PricingRule rule = createBasicRule(STATION_ID,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 10);
        // loadAllRules: 站级→[rule], 全局峰谷→[]
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(rule))
                .thenReturn(Collections.emptyList());

        LocalDateTime startTime = LocalDateTime.of(2024, 6, 1, 10, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 6, 1, 11, 0);
        BigDecimal totalEnergy = new BigDecimal("100");

        // Act
        FeeDetail result = pricingService.calculateExactFee(
                STATION_ID, startTime, endTime, totalEnergy, TEST_SN);

        // Assert
        assertEquals(0, result.electricityFee().compareTo(new BigDecimal("80.00")),
                "电费应为 100 × 0.8000 = 80.00");
        assertEquals(0, result.serviceFee().compareTo(new BigDecimal("20.00")),
                "服务费应为 100 × 0.2000 = 20.00");
        assertEquals(0, result.totalAmount().compareTo(new BigDecimal("100.00")),
                "总金额应为 80.00 + 20.00 = 100.00");
    }

    @Test
    @DisplayName("calculateExactFee - totalEnergy=0 → 返回零费用，不查 DB 和 Redis")
    void calculateExactFee_ZeroEnergy_ReturnsZeroFee() {
        /*
         * 测试场景：充电量为 0
         * 预期：直接返回 FeeDetail(0, 0, 0)，不需要查询计费规则和 Redis
         */
        // Act
        LocalDateTime startTime = LocalDateTime.of(2024, 6, 1, 10, 0);
        LocalDateTime endTime = LocalDateTime.of(2024, 6, 1, 11, 0);
        FeeDetail result = pricingService.calculateExactFee(
                STATION_ID, startTime, endTime, BigDecimal.ZERO, TEST_SN);

        // Assert
        assertEquals(BigDecimal.ZERO, result.electricityFee(), "电费应为 0");
        assertEquals(BigDecimal.ZERO, result.serviceFee(), "服务费应为 0");
        assertEquals(BigDecimal.ZERO, result.totalAmount(), "总金额应为 0");
        verifyNoInteractions(pricingRuleMapper);
        verifyNoInteractions(redisTemplate);
    }

    // ==================== 缓存相关测试 ====================

    @Test
    @DisplayName("规则缓存 - 第二次查询同一站 → 走缓存，不查 DB")
    void cache_SecondQuerySameStation_UsesCachedRules() {
        /*
         * 测试场景：先查一次 stationId=1 的规则，再查一次
         * 预期：第二次查询走缓存，不再调用 selectList
         */
        // Arrange
        PricingRule rule = createBasicRule(STATION_ID,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 10);
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(rule))
                .thenReturn(Collections.emptyList());

        // Act - 第一次查询（写缓存）
        PricingRule result1 = pricingService.getEffectivePricing(STATION_ID, LocalDateTime.now());
        assertNotNull(result1);

        // Act - 第二次查询（走缓存，不再调 selectList）
        // 如果走缓存，则不再调用 selectList（之前的 mock 已耗尽，再调会抛异常）
        PricingRule result2 = pricingService.getEffectivePricing(STATION_ID, LocalDateTime.now());

        // Assert
        assertNotNull(result2);
        assertEquals(result1.getElectricityPrice(), result2.getElectricityPrice());

        // 验证只调用了2次 selectList（站级1次 + 全局峰谷1次），而不是4次
        verify(pricingRuleMapper, times(2)).selectList(any());
    }

    @Test
    @DisplayName("规则缓存 - evictRulesCache 后 → 重新查 DB")
    void cache_AfterEvict_ReloadsFromDb() {
        /*
         * 测试场景：查一次→清除缓存→再查一次
         * 预期：清除缓存后需要重新查 DB
         */
        // Arrange
        PricingRule rule = createBasicRule(STATION_ID,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 10);
        // 第一次 loadAllRules 需要2次调用
        // 清除缓存后第二次 loadAllRules 又需要2次调用，共4次
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(rule))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(rule))
                .thenReturn(Collections.emptyList());

        // Act - 第一次查询
        pricingService.getEffectivePricing(STATION_ID, LocalDateTime.now());

        // 清除缓存
        pricingService.evictRulesCache(STATION_ID);

        // Act - 第二次查询（重新查 DB）
        pricingService.getEffectivePricing(STATION_ID, LocalDateTime.now());

        // Assert - 共4次 selectList 调用
        verify(pricingRuleMapper, times(4)).selectList(any());
    }
}
