package com.iot.api.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.api.security.SecurityUtil;
import com.iot.common.model.PageResult;
import com.iot.common.model.Result;
import com.iot.core.entity.PricingRule;
import com.iot.core.service.PricingRuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * 运营后台 - 计费规则管理控制器
 * <p>
 * 提供计费规则的增删改查接口。
 * 所有接口需要 ROLE_ADMIN 权限（由 SecurityConfig 控制）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/pricing")
@RequiredArgsConstructor
public class AdminPricingController {

    private final PricingRuleService pricingRuleService;

    /**
     * 分页查询计费规则列表
     * <p>
     * 支持按充电站ID和规则状态筛选。
     * </p>
     *
     * @param page      页码，默认1
     * @param size      每页数量，默认20
     * @param stationId 充电站ID（可选，null表示全部）
     * @param status    状态（可选，null表示全部）
     * @return 分页计费规则列表
     */
    @GetMapping("/list")
    public Result<PageResult<PricingRule>> listRules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Integer status) {

        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-计费规则列表] 操作人: {}, stationId: {}, status: {}, page: {}",
                operatorId, stationId, status, page);

        Page<PricingRule> result = pricingRuleService.listRules(stationId, status, page, size);
        return Result.success(PageResult.of(
                result.getRecords(), result.getTotal(), page, size));
    }

    /**
     * 获取计费规则详情
     *
     * @param id 规则ID
     * @return 计费规则详情
     */
    @GetMapping("/{id}")
    public Result<PricingRule> getRuleDetail(@PathVariable Long id) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-计费规则详情] 操作人: {}, ruleId: {}", operatorId, id);

        PricingRule rule = pricingRuleService.getRuleDetail(id);
        return Result.success(rule);
    }

    /**
     * 新增计费规则
     *
     * @param request 计费规则请求体
     * @return 新增的计费规则
     */
    @PostMapping
    public Result<PricingRule> createRule(@RequestBody @Valid PricingRuleRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-新增计费规则] 操作人: {}, name: {}, stationId: {}, ruleType: {}",
                operatorId, request.getName(), request.getStationId(), request.getRuleType());

        PricingRule rule = toEntity(request);
        PricingRule created = pricingRuleService.createRule(rule);
        return Result.success(created);
    }

    /**
     * 修改计费规则
     *
     * @param id      规则ID
     * @param request 计费规则请求体
     * @return 修改后的计费规则
     */
    @PutMapping("/{id}")
    public Result<PricingRule> updateRule(@PathVariable Long id,
                                           @RequestBody @Valid PricingRuleRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-修改计费规则] 操作人: {}, ruleId: {}, name: {}", operatorId, id, request.getName());

        PricingRule rule = toEntity(request);
        rule.setId(id);
        PricingRule updated = pricingRuleService.updateRule(rule);
        return Result.success(updated);
    }

    /**
     * 删除计费规则（物理删除）
     * <p>
     * 建议先禁用规则，确认无影响后再删除。
     * </p>
     *
     * @param id 规则ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteRule(@PathVariable Long id) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-删除计费规则] 操作人: {}, ruleId: {}", operatorId, id);

        pricingRuleService.deleteRule(id);
        return Result.success("删除成功");
    }

    /**
     * 修改计费规则启用/禁用状态
     *
     * @param id     规则ID
     * @param status 状态：0-禁用，1-启用
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    public Result<String> updateStatus(@PathVariable Long id,
                                        @RequestParam @NotNull Integer status) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-计费规则状态] 操作人: {}, ruleId: {}, status: {}", operatorId, id, status);

        if (status != 0 && status != 1) {
            return Result.error(400, "状态值必须为0（禁用）或1（启用）");
        }
        pricingRuleService.updateStatus(id, status);
        return Result.success(status == 1 ? "已启用" : "已禁用");
    }

    /**
     * 获取指定充电站所有启用的计费规则
     * <p>
     * 用于管理和查看某个充电站下的所有可用规则。
     * </p>
     *
     * @param stationId 充电站ID
     * @return 计费规则列表
     */
    @GetMapping("/station/{stationId}")
    public Result<List<PricingRule>> getStationRules(@PathVariable Long stationId) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-站点计费规则] 操作人: {}, stationId: {}", operatorId, stationId);

        List<PricingRule> rules = pricingRuleService.getEnabledRulesByStation(stationId);
        return Result.success(rules);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 请求体 → 实体转换
     */
    private PricingRule toEntity(PricingRuleRequest request) {
        PricingRule rule = new PricingRule();
        rule.setName(request.getName());
        rule.setStationId(request.getStationId());
        rule.setRuleType(request.getRuleType());
        rule.setElectricityPrice(request.getElectricityPrice());
        rule.setServicePrice(request.getServicePrice());
        rule.setPriority(request.getPriority());
        rule.setStatus(request.getStatus());

        // 峰谷电价才设置时段
        if (request.getRuleType() != null && request.getRuleType() == 2) {
            rule.setStartTime(request.getStartTime());
            rule.setEndTime(request.getEndTime());
        }
        return rule;
    }

    /**
     * 计费规则请求体
     */
    @Data
    public static class PricingRuleRequest {

        /** 规则名称 */
        @NotBlank(message = "规则名称不能为空")
        private String name;

        /** 所属充电站ID（0表示全局规则） */
        @NotNull(message = "充电站ID不能为空")
        private Long stationId;

        /** 规则类型：1-基础电价，2-峰谷电价 */
        @NotNull(message = "规则类型不能为空")
        private Integer ruleType;

        /** 时段开始时间（峰谷电价必填） */
        private LocalTime startTime;

        /** 时段结束时间（峰谷电价必填） */
        private LocalTime endTime;

        /** 电价(元/kWh) */
        @NotNull(message = "电价不能为空")
        private BigDecimal electricityPrice;

        /** 服务费(元/kWh) */
        @NotNull(message = "服务费不能为空")
        private BigDecimal servicePrice;

        /** 优先级（数字越大越优先匹配） */
        private Integer priority;

        /** 状态：0-禁用，1-启用 */
        private Integer status;
    }
}
