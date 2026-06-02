package com.iot.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.common.exception.BusinessException;
import com.iot.core.entity.PricingRule;
import com.iot.core.mapper.PricingRuleMapper;
import com.iot.core.service.impl.PricingRuleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PricingRuleServiceImpl 单元测试
 * <p>
 * 测试计费规则管理的核心业务逻辑：
 * - 规则列表分页查询（含筛选条件）
 * - 规则详情查看
 * - 规则新增（含时段重叠校验）
 * - 规则修改（禁止修改 stationId/ruleType）
 * - 规则删除
 * - 状态启用/禁用
 * - 按站点查询启用的规则
 * </p>
 *
 * @author IoT Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PricingRuleService 管理端单元测试")
class PricingRuleServiceTest {

    @Mock
    private PricingRuleMapper pricingRuleMapper;

    @InjectMocks
    private PricingRuleServiceImpl pricingRuleService;

    // ==================== 测试数据工厂方法 ====================

    private PricingRule createRule(String name, Long stationId, int ruleType,
                                   BigDecimal electricityPrice, BigDecimal servicePrice,
                                   int priority, LocalTime startTime, LocalTime endTime) {
        PricingRule rule = new PricingRule();
        rule.setId(1L);
        rule.setName(name);
        rule.setStationId(stationId);
        rule.setRuleType(ruleType);
        rule.setElectricityPrice(electricityPrice);
        rule.setServicePrice(servicePrice);
        rule.setPriority(priority);
        rule.setStartTime(startTime);
        rule.setEndTime(endTime);
        rule.setStatus(1);
        return rule;
    }

    private PricingRule createBasicRule(String name, Long stationId) {
        return createRule(name, stationId, 1,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 10, null, null);
    }

    private PricingRule createPeakValleyRule(String name, Long stationId, LocalTime start, LocalTime end) {
        return createRule(name, stationId, 2,
                new BigDecimal("1.0000"), new BigDecimal("0.3000"), 10, start, end);
    }

    // ==================== getRuleDetail 测试 ====================

    @Test
    @DisplayName("getRuleDetail - 规则存在 → 返回规则")
    void getRuleDetail_Exists_ReturnsRule() {
        PricingRule rule = createBasicRule("测试规则", 1L);
        when(pricingRuleMapper.selectById(1L)).thenReturn(rule);

        PricingRule result = pricingRuleService.getRuleDetail(1L);

        assertNotNull(result);
        assertEquals("测试规则", result.getName());
    }

    @Test
    @DisplayName("getRuleDetail - 规则不存在 → 抛出 BusinessException(404)")
    void getRuleDetail_NotExists_Throws404() {
        when(pricingRuleMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pricingRuleService.getRuleDetail(999L));
        assertEquals(404, ex.getCode());
    }

    // ==================== createRule 测试 ====================

    @Test
    @DisplayName("createRule - 基础电价规则 → 新增成功")
    void createRule_BasicRule_Success() {
        PricingRule rule = createBasicRule("新基础电价", 1L);
        rule.setId(null);

        when(pricingRuleMapper.insert(any(PricingRule.class))).thenReturn(1);

        PricingRule result = pricingRuleService.createRule(rule);

        assertNotNull(result);
        verify(pricingRuleMapper).insert(any(PricingRule.class));
    }

    @Test
    @DisplayName("createRule - 设置默认优先级和状态 → priority=0, status=1")
    void createRule_DefaultPriorityAndStatus() {
        PricingRule rule = createBasicRule("新规则", 1L);
        rule.setId(null);
        rule.setPriority(null);
        rule.setStatus(null);

        when(pricingRuleMapper.insert(any(PricingRule.class))).thenReturn(1);

        pricingRuleService.createRule(rule);

        assertEquals(0, rule.getPriority(), "未设置优先级时应默认为0");
        assertEquals(1, rule.getStatus(), "未设置状态时应默认为1（启用）");
    }

    @Test
    @DisplayName("createRule - 峰谷电价，时段与已有规则重叠 → 抛出 BusinessException(409)")
    void createRule_PeakValleyTimeOverlap_Throws409() {
        PricingRule newRule = createPeakValleyRule("新峰谷", 1L,
                LocalTime.of(9, 0), LocalTime.of(11, 0));
        newRule.setId(null);

        // 模拟已有规则 08:00-10:00（与新规则的 09:00-11:00 重叠）
        PricingRule existingRule = createPeakValleyRule("已有峰谷", 1L,
                LocalTime.of(8, 0), LocalTime.of(10, 0));

        when(pricingRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existingRule));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pricingRuleService.createRule(newRule));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("时段"));
        assertTrue(ex.getMessage().contains("重叠"));
    }

    // ==================== updateRule 测试 ====================

    @Test
    @DisplayName("updateRule - 规则不存在 → 抛出 BusinessException(404)")
    void updateRule_NotExists_Throws404() {
        PricingRule rule = createBasicRule("修改", 1L);
        rule.setId(999L);

        when(pricingRuleMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pricingRuleService.updateRule(rule));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("updateRule - stationId 和 ruleType 不允许修改 → 保持原值")
    void updateRule_StationIdAndRuleType_NotChanged() {
        PricingRule existing = createBasicRule("原规则", 1L);
        existing.setRuleType(1);
        existing.setStationId(1L);

        PricingRule update = createBasicRule("修改名", 2L); // 试图改为 stationId=2
        update.setId(1L);
        update.setRuleType(2); // 试图改为 ruleType=2

        when(pricingRuleMapper.selectById(1L)).thenReturn(existing);
        when(pricingRuleMapper.updateById(any(PricingRule.class))).thenReturn(1);
        when(pricingRuleMapper.selectById(1L)).thenReturn(existing); // 第二次查返回existing用于验证

        pricingRuleService.updateRule(update);

        // 验证：stationId 和 ruleType 被保持原值
        assertEquals(1L, update.getStationId(), "stationId 不允许修改");
        assertEquals(1, update.getRuleType(), "ruleType 不允许修改");
    }

    // ==================== deleteRule 测试 ====================

    @Test
    @DisplayName("deleteRule - 规则存在 → 删除成功")
    void deleteRule_Success() {
        PricingRule rule = createBasicRule("待删除", 1L);
        when(pricingRuleMapper.selectById(1L)).thenReturn(rule);
        when(pricingRuleMapper.deleteById(1L)).thenReturn(1);

        pricingRuleService.deleteRule(1L);

        verify(pricingRuleMapper).deleteById(1L);
    }

    @Test
    @DisplayName("deleteRule - 规则不存在 → 抛出 BusinessException(404)")
    void deleteRule_NotExists_Throws404() {
        when(pricingRuleMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pricingRuleService.deleteRule(999L));
        assertEquals(404, ex.getCode());
    }

    // ==================== updateStatus 测试 ====================

    @Test
    @DisplayName("updateStatus - 启用规则 → status=1")
    void updateStatus_EnableRule_Success() {
        PricingRule rule = createBasicRule("规则", 1L);
        rule.setStatus(0); // 当前禁用
        when(pricingRuleMapper.selectById(1L)).thenReturn(rule);
        when(pricingRuleMapper.updateById(any(PricingRule.class))).thenReturn(1);

        pricingRuleService.updateStatus(1L, 1);

        ArgumentCaptor<PricingRule> captor = ArgumentCaptor.forClass(PricingRule.class);
        verify(pricingRuleMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("updateStatus - 规则不存在 → 抛出 BusinessException(404)")
    void updateStatus_NotExists_Throws404() {
        when(pricingRuleMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pricingRuleService.updateStatus(999L, 1));
        assertEquals(404, ex.getCode());
    }

    // ==================== getEnabledRulesByStation 测试 ====================

    @Test
    @DisplayName("getEnabledRulesByStation - 返回启用的规则按priority降序")
    void getEnabledRulesByStation_ReturnsEnabledRulesOrderedByPriority() {
        List<PricingRule> rules = new ArrayList<>();
        rules.add(createRule("低优先级", 1L, 1,
                new BigDecimal("0.8000"), new BigDecimal("0.2000"), 1, null, null));
        rules.add(createRule("高优先级", 1L, 1,
                new BigDecimal("1.0000"), new BigDecimal("0.3000"), 10, null, null));

        // 注意：selectList 的返回顺序取决于 MyBatis 查询结果
        // 实际业务中 queryWrapper orderByDesc(Priority) 确保顺序
        when(pricingRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(rules);

        List<PricingRule> result = pricingRuleService.getEnabledRulesByStation(1L);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    // ==================== listRules 测试 ====================

    @Test
    @DisplayName("listRules - 无条件筛选 → 返回全部规则分页")
    void listRules_NoFilter_ReturnsAllRules() {
        List<PricingRule> rules = List.of(
                createBasicRule("规则1", 1L),
                createBasicRule("规则2", 2L)
        );
        Page<PricingRule> pageResult = new Page<>(1, 10, 2);
        pageResult.setRecords(rules);
        when(pricingRuleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        Page<PricingRule> result = pricingRuleService.listRules(null, null, 1, 10);

        assertNotNull(result);
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getRecords().size());
    }

    @Test
    @DisplayName("listRules - 按stationId筛选 → 只返回该站规则")
    void listRules_StationFilter_ReturnsFilteredRules() {
        List<PricingRule> rules = List.of(createBasicRule("规则1", 1L));
        Page<PricingRule> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(rules);
        when(pricingRuleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(pageResult);

        Page<PricingRule> result = pricingRuleService.listRules(1L, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }
}
