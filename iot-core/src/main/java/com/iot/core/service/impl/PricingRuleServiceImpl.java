package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.common.exception.BusinessException;
import com.iot.core.entity.PricingRule;
import com.iot.core.mapper.PricingRuleMapper;
import com.iot.core.service.PricingRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 计费规则管理服务实现类
 * <p>
 * 提供计费规则的增删改查具体实现，供运营后台使用。
 * 规则管理操作需要管理员权限，由 Controller 层通过 Security 控制。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingRuleServiceImpl implements PricingRuleService {

    private final PricingRuleMapper pricingRuleMapper;

    /** 规则类型：峰谷电价 */
    private static final int RULE_TYPE_PEAK_VALLEY = 2;

    // ==================== 查询 ====================

    /**
     * 分页查询计费规则列表
     * <p>
     * 支持按充电站ID筛选（null表示全部）和规则状态筛选（null表示全部），
     * 结果按充电站ID和优先级降序排列。
     * </p>
     */
    @Override
    public Page<PricingRule> listRules(Long stationId, Integer status, int page, int size) {
        LambdaQueryWrapper<PricingRule> wrapper = new LambdaQueryWrapper<>();
        if (stationId != null) {
            wrapper.eq(PricingRule::getStationId, stationId);
        }
        if (status != null) {
            wrapper.eq(PricingRule::getStatus, status);
        }
        // 按站ID升序、优先级降序排列，便于运营人员查看
        wrapper.orderByAsc(PricingRule::getStationId)
               .orderByDesc(PricingRule::getPriority);

        return pricingRuleMapper.selectPage(Page.of(page, size), wrapper);
    }

    /**
     * 获取计费规则详情
     */
    @Override
    public PricingRule getRuleDetail(Long id) {
        PricingRule rule = pricingRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "计费规则不存在");
        }
        return rule;
    }

    // ==================== 新增/修改/删除 ====================

    /**
     * 新增计费规则
     * <p>
     * 新增前校验同一充电站下峰谷电价的时段是否与已有规则重叠，
     * 避免同一时段存在两条冲突的计费规则。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PricingRule createRule(PricingRule rule) {
        // 1. 峰谷电价需要校验时段是否重叠
        if (rule.getRuleType() != null && rule.getRuleType() == RULE_TYPE_PEAK_VALLEY
                && rule.getStartTime() != null && rule.getEndTime() != null) {
            validateNoTimeOverlap(rule.getStationId(), null, rule.getStartTime(), rule.getEndTime());
        }

        // 2. 设置默认值
        if (rule.getPriority() == null) {
            rule.setPriority(0);
        }
        if (rule.getStatus() == null) {
            rule.setStatus(1);
        }

        pricingRuleMapper.insert(rule);
        log.info("[计费规则] 新增成功 - id: {}, name: {}, stationId: {}, ruleType: {}",
                rule.getId(), rule.getName(), rule.getStationId(), rule.getRuleType());
        return rule;
    }

    /**
     * 修改计费规则
     * <p>
     * 不允许修改 stationId 和 ruleType（保持规则归属和类型不变）。
     * 如果修改了时段，需要校验是否与同站其他峰谷电价规则冲突。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PricingRule updateRule(PricingRule rule) {
        // 1. 确保规则存在
        PricingRule existing = pricingRuleMapper.selectById(rule.getId());
        if (existing == null) {
            throw new BusinessException(404, "计费规则不存在");
        }

        // 2. stationId 和 ruleType 不允许修改
        rule.setStationId(existing.getStationId());
        rule.setRuleType(existing.getRuleType());

        // 3. 峰谷电价需要校验时段是否重叠（排除自身）
        if (existing.getRuleType() == RULE_TYPE_PEAK_VALLEY
                && rule.getStartTime() != null && rule.getEndTime() != null) {
            validateNoTimeOverlap(rule.getStationId(), rule.getId(), rule.getStartTime(), rule.getEndTime());
        }

        pricingRuleMapper.updateById(rule);
        log.info("[计费规则] 修改成功 - id: {}, name: {}", rule.getId(), rule.getName());
        return pricingRuleMapper.selectById(rule.getId());
    }

    /**
     * 删除计费规则（物理删除）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        PricingRule rule = pricingRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "计费规则不存在");
        }
        pricingRuleMapper.deleteById(id);
        log.info("[计费规则] 删除成功 - id: {}, name: {}", id, rule.getName());
    }

    /**
     * 修改计费规则启用/禁用状态
     * <p>
     * 禁用规则不会影响已生成的订单，但会影响后续的计费计算。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        PricingRule rule = pricingRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "计费规则不存在");
        }
        rule.setStatus(status);
        pricingRuleMapper.updateById(rule);
        log.info("[计费规则] 状态更新 - id: {}, status: {} -> {}", id,
                rule.getStatus() == 1 ? "启用" : "禁用",
                status == 1 ? "启用" : "禁用");
    }

    /**
     * 获取指定充电站所有启用的计费规则
     * <p>
     * 按优先级降序排列，供计费计算使用。
     * </p>
     */
    @Override
    public List<PricingRule> getEnabledRulesByStation(Long stationId) {
        return pricingRuleMapper.selectList(
                new LambdaQueryWrapper<PricingRule>()
                        .eq(PricingRule::getStationId, stationId)
                        .eq(PricingRule::getStatus, 1)
                        .orderByDesc(PricingRule::getPriority)
        );
    }

    // ==================== 私有校验方法 ====================

    /**
     * 校验同一充电站下峰谷电价规则的时段是否重叠
     * <p>
     * 时段重叠判定：
     * - 两条规则的时段（startTime, endTime）存在交集
     * - 排除自身（excludeId 参数）
     * - 跨天时段（如 22:00-06:00）也参与校验
     * </p>
     *
     * @param stationId 充电站ID
     * @param excludeId 需要排除的规则ID（修改时排除自身）
     * @param startTime 新规则的开始时间
     * @param endTime   新规则的结束时间
     * @throws BusinessException 时段重叠时抛出
     */
    private void validateNoTimeOverlap(Long stationId, Long excludeId,
                                       java.time.LocalTime startTime, java.time.LocalTime endTime) {
        // 查询同站所有启用的峰谷电价规则
        List<PricingRule> existingPeakValleyRules = pricingRuleMapper.selectList(
                new LambdaQueryWrapper<PricingRule>()
                        .eq(PricingRule::getStationId, stationId)
                        .eq(PricingRule::getRuleType, RULE_TYPE_PEAK_VALLEY)
                        .eq(PricingRule::getStatus, 1)
        );

        for (PricingRule existing : existingPeakValleyRules) {
            // 排除自身
            if (excludeId != null && existing.getId().equals(excludeId)) {
                continue;
            }
            // 跳过没有时段信息的规则
            if (existing.getStartTime() == null || existing.getEndTime() == null) {
                continue;
            }
            // 检查时段是否重叠
            if (isTimeOverlap(startTime, endTime, existing.getStartTime(), existing.getEndTime())) {
                throw new BusinessException(409,
                        String.format("时段与已有规则「%s」(%s-%s) 重叠",
                                existing.getName(), existing.getStartTime(), existing.getEndTime()));
            }
        }
    }

    /**
     * 判断两个时间段是否有重叠
     * <p>
     * 支持跨天时段的判断。
     * 两个跨天时段（如 22:00-06:00 和 22:00-06:00）必定重叠。
     * 一个跨天一个不跨天：如 22:00-06:00（跨天）和 08:00-10:00（不跨天）不重叠；
     * 22:00-06:00（跨天）和 05:00-09:00（不跨天）重叠（05:00在跨天时段内）。
     * </p>
     *
     * @param start1 时段1开始
     * @param end1   时段1结束
     * @param start2 时段2开始
     * @param end2   时段2结束
     * @return 重叠返回 true
     */
    private boolean isTimeOverlap(java.time.LocalTime start1, java.time.LocalTime end1,
                                   java.time.LocalTime start2, java.time.LocalTime end2) {
        boolean cross1 = !start1.isBefore(end1); // 跨天
        boolean cross2 = !start2.isBefore(end2); // 跨天

        if (!cross1 && !cross2) {
            // 两个都不跨天：有交集即可
            return start1.isBefore(end2) && start2.isBefore(end1);
        } else if (cross1 && cross2) {
            // 两个都跨天：必定重叠
            return true;
        } else if (cross1 && !cross2) {
            // 时段1跨天（如 22:00-06:00），时段2不跨天（如 08:00-10:00）
            // 时段2的任何时间在 22:00-23:59 或 00:00-06:00 范围内即重叠
            return start2.isBefore(end1) // 时段2开始 < 时段1结束（在午夜前）
                    || start1.isBefore(end2); // 时段1开始 < 时段2结束（在午夜后）
        } else {
            // 时段2跨天，时段1不跨天 — 对称处理
            return start1.isBefore(end2) || start2.isBefore(end1);
        }
    }
}
