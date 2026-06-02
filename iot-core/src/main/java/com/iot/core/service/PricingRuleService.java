package com.iot.core.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.core.entity.PricingRule;

import java.util.List;

/**
 * 计费规则管理服务接口
 * <p>
 * 提供计费规则的增删改查功能，供运营后台使用。
 * 区分于 {@link PricingService}（计费引擎），本接口专注于规则数据的维护管理。
 * </p>
 *
 * @author IoT Team
 */
public interface PricingRuleService {

    /**
     * 分页查询计费规则列表
     * <p>
     * 支持按充电站ID和规则状态筛选。
     * </p>
     *
     * @param stationId 充电站ID（可选，null表示全部）
     * @param status    规则状态（可选，null表示全部）
     * @param page      页码
     * @param size      每页数量
     * @return 分页计费规则列表
     */
    Page<PricingRule> listRules(Long stationId, Integer status, int page, int size);

    /**
     * 获取计费规则详情
     *
     * @param id 规则ID
     * @return 计费规则，不存在时返回 null
     */
    PricingRule getRuleDetail(Long id);

    /**
     * 新增计费规则
     * <p>
     * 新增前会校验同一充电站下是否存在时段重叠的峰谷电价规则。
     * </p>
     *
     * @param rule 计费规则（id 为空，由数据库自动生成）
     * @return 新增后的计费规则（含 id）
     * @throws com.iot.common.exception.BusinessException 时段冲突时抛出
     */
    PricingRule createRule(PricingRule rule);

    /**
     * 修改计费规则
     * <p>
     * 只允许修改名称、电价、服务费、时段、优先级、状态等字段，
     * 不允许修改 stationId 和 ruleType。
     * </p>
     *
     * @param rule 计费规则（id 必须存在）
     * @return 修改后的计费规则
     * @throws com.iot.common.exception.BusinessException 规则不存在或时段冲突时抛出
     */
    PricingRule updateRule(PricingRule rule);

    /**
     * 删除计费规则（物理删除）
     * <p>
     * 注意：正在被使用的规则不应删除，建议先禁用再删除。
     * </p>
     *
     * @param id 规则ID
     * @throws com.iot.common.exception.BusinessException 规则不存在时抛出
     */
    void deleteRule(Long id);

    /**
     * 修改计费规则启用/禁用状态
     *
     * @param id     规则ID
     * @param status 状态：0-禁用，1-启用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 获取指定充电站的所有启用的计费规则
     *
     * @param stationId 充电站ID（0表示全局）
     * @return 计费规则列表（按 priority 降序）
     */
    List<PricingRule> getEnabledRulesByStation(Long stationId);
}
