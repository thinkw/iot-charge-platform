package com.iot.api.controller.admin;

import com.iot.api.security.SecurityUtil;
import com.iot.common.model.PageResult;
import com.iot.common.model.Result;
import com.iot.core.entity.Charger;
import com.iot.core.service.ChargerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 运营后台 - 充电桩管理控制器
 * <p>
 * 提供充电桩的增删改查和启禁用管理接口。
 * 所有接口需要 ROLE_ADMIN 权限（由 SecurityConfig 控制）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/charger")
@RequiredArgsConstructor
public class AdminChargerController {

    private final ChargerService chargerService;

    /**
     * 分页查询充电桩列表
     * <p>
     * 支持按充电站、SN模糊搜索、状态筛选。
     * </p>
     *
     * @param page      页码，默认1
     * @param size      每页数量，默认20
     * @param stationId 充电站ID（可选）
     * @param sn        设备SN（可选，模糊搜索）
     * @param status    状态（可选）
     * @return 分页充电桩列表
     */
    @GetMapping("/list")
    public Result<PageResult<Charger>> listChargers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) String sn,
            @RequestParam(required = false) Integer status) {

        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-充电桩列表] 操作人: {}, stationId: {}, sn: {}, status: {}, page: {}",
                operatorId, stationId, sn, status, page);

        PageResult<Charger> result = chargerService.adminListChargers(stationId, sn, status, page, size);
        return Result.success(result);
    }

    /**
     * 获取充电桩详情
     *
     * @param id 充电桩ID
     * @return 充电桩详情
     */
    @GetMapping("/{id}")
    public Result<Charger> getChargerDetail(@PathVariable Long id) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-充电桩详情] 操作人: {}, chargerId: {}", operatorId, id);

        Charger charger = chargerService.adminGetCharger(id);
        return Result.success(charger);
    }

    /**
     * 新增充电桩
     *
     * @param request 充电桩信息
     * @return 新增的充电桩
     */
    @PostMapping
    public Result<Charger> createCharger(@RequestBody @Valid ChargerRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-新增充电桩] 操作人: {}, sn: {}, stationId: {}", operatorId, request.getSn(), request.getStationId());

        Charger charger = toEntity(request);
        Charger created = chargerService.adminCreateCharger(charger);
        return Result.success(created);
    }

    /**
     * 修改充电桩
     * <p>
     * SN 和所属充电站不允许修改。
     * </p>
     *
     * @param id      充电桩ID
     * @param request 充电桩信息
     * @return 修改后的充电桩
     */
    @PutMapping("/{id}")
    public Result<Charger> updateCharger(@PathVariable Long id,
                                          @RequestBody @Valid ChargerRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-修改充电桩] 操作人: {}, chargerId: {}, name: {}", operatorId, id, request.getName());

        Charger charger = toEntity(request);
        charger.setId(id);
        Charger updated = chargerService.adminUpdateCharger(charger);
        return Result.success(updated);
    }

    /**
     * 删除充电桩
     * <p>
     * 删除前检查是否有进行中的充电订单。
     * </p>
     *
     * @param id 充电桩ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteCharger(@PathVariable Long id) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-删除充电桩] 操作人: {}, chargerId: {}", operatorId, id);

        chargerService.adminDeleteCharger(id);
        return Result.success("删除成功");
    }

    /**
     * 修改充电桩启禁用状态
     * <p>
     * 启用后设备状态变为空闲，禁用后变为离线。
     * </p>
     *
     * @param id     充电桩ID
     * @param status 状态：0-禁用，1-启用
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    public Result<String> updateStatus(@PathVariable Long id,
                                        @RequestParam @NotNull Integer status) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-充电桩状态] 操作人: {}, chargerId: {}, status: {}", operatorId, id, status);

        if (status != 0 && status != 1) {
            return Result.error(400, "状态值必须为0（禁用）或1（启用）");
        }
        chargerService.adminUpdateChargerStatus(id, status);
        return Result.success(status == 1 ? "已启用" : "已禁用");
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 请求体 → 实体转换
     */
    private Charger toEntity(ChargerRequest request) {
        Charger charger = new Charger();
        charger.setSn(request.getSn());
        charger.setName(request.getName());
        charger.setStationId(request.getStationId());
        charger.setPower(request.getPower());
        charger.setStatus(request.getStatus());
        return charger;
    }

    /**
     * 充电桩请求体
     */
    @Data
    public static class ChargerRequest {

        /** 设备唯一序列号 */
        @NotBlank(message = "设备SN不能为空")
        private String sn;

        /** 充电桩名称 */
        private String name;

        /** 所属充电站ID */
        @NotNull(message = "所属充电站不能为空")
        private Long stationId;

        /** 额定功率(kW) */
        private BigDecimal power;

        /** 状态 */
        private Integer status;
    }
}
