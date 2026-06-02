package com.iot.api.controller.admin;

import com.iot.api.security.SecurityUtil;
import com.iot.common.model.PageResult;
import com.iot.common.model.Result;
import com.iot.core.entity.Station;
import com.iot.core.service.StationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 运营后台 - 充电站管理控制器
 * <p>
 * 提供充电站的增删改查和状态管理接口。
 * 所有接口需要 ROLE_ADMIN 权限（由 SecurityConfig 控制）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/station")
@RequiredArgsConstructor
public class AdminStationController {

    private final StationService stationService;

    /**
     * 分页查询充电站列表
     * <p>
     * 支持按名称模糊搜索和状态筛选。
     * </p>
     *
     * @param page   页码，默认1
     * @param size   每页数量，默认20
     * @param name   站点名称（可选，模糊搜索）
     * @param status 状态（可选）：0-暂停营业，1-营业中，2-维护中
     * @return 分页充电站列表
     */
    @GetMapping("/list")
    public Result<PageResult<Station>> listStations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {

        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-充电站列表] 操作人: {}, name: {}, status: {}, page: {}", operatorId, name, status, page);

        PageResult<Station> result = stationService.adminListStations(name, status, page, size);
        return Result.success(result);
    }

    /**
     * 获取充电站详情
     *
     * @param id 充电站ID
     * @return 充电站详情
     */
    @GetMapping("/{id}")
    public Result<Station> getStationDetail(@PathVariable Long id) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-充电站详情] 操作人: {}, stationId: {}", operatorId, id);

        Station station = stationService.adminGetStation(id);
        return Result.success(station);
    }

    /**
     * 新增充电站
     *
     * @param request 充电站信息
     * @return 新增的充电站
     */
    @PostMapping
    public Result<Station> createStation(@RequestBody @Valid StationRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-新增充电站] 操作人: {}, name: {}", operatorId, request.getName());

        Station station = toEntity(request);
        Station created = stationService.adminCreateStation(station);
        return Result.success(created);
    }

    /**
     * 修改充电站
     *
     * @param id      充电站ID
     * @param request 充电站信息
     * @return 修改后的充电站
     */
    @PutMapping("/{id}")
    public Result<Station> updateStation(@PathVariable Long id,
                                          @RequestBody @Valid StationRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-修改充电站] 操作人: {}, stationId: {}, name: {}", operatorId, id, request.getName());

        Station station = toEntity(request);
        station.setId(id);
        Station updated = stationService.adminUpdateStation(station);
        return Result.success(updated);
    }

    /**
     * 删除充电站
     * <p>
     * 删除前检查是否有关联的充电桩，有关联则不允许删除。
     * </p>
     *
     * @param id 充电站ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteStation(@PathVariable Long id) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-删除充电站] 操作人: {}, stationId: {}", operatorId, id);

        stationService.adminDeleteStation(id);
        return Result.success("删除成功");
    }

    /**
     * 修改充电站营业状态
     *
     * @param id     充电站ID
     * @param status 状态：0-暂停营业，1-营业中，2-维护中
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    public Result<String> updateStatus(@PathVariable Long id,
                                        @RequestParam @NotNull Integer status) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-充电站状态] 操作人: {}, stationId: {}, status: {}", operatorId, id, status);

        if (status < 0 || status > 2) {
            return Result.error(400, "状态值必须为0（暂停营业）、1（营业中）或2（维护中）");
        }
        stationService.adminUpdateStationStatus(id, status);
        return Result.success(status == 0 ? "已暂停营业" : status == 1 ? "已恢复营业" : "已设为维护中");
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 请求体 → 实体转换
     */
    private Station toEntity(StationRequest request) {
        Station station = new Station();
        station.setName(request.getName());
        station.setAddress(request.getAddress());
        station.setLongitude(request.getLongitude());
        station.setLatitude(request.getLatitude());
        station.setBusinessHours(request.getBusinessHours());
        station.setContact(request.getContact());
        station.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        return station;
    }

    /**
     * 充电站请求体
     */
    @Data
    public static class StationRequest {

        /** 充电站名称 */
        @NotBlank(message = "充电站名称不能为空")
        private String name;

        /** 详细地址 */
        private String address;

        /** 经度 */
        private BigDecimal longitude;

        /** 纬度 */
        private BigDecimal latitude;

        /** 营业时间 */
        private String businessHours;

        /** 联系电话 */
        private String contact;

        /** 状态：0-暂停营业，1-营业中，2-维护中 */
        private Integer status;
    }
}
