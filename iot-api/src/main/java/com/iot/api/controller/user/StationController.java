package com.iot.api.controller.user;

import com.iot.common.model.PageResult;
import com.iot.common.model.Result;
import com.iot.core.dto.response.ChargerVO;
import com.iot.core.dto.response.StationDetailVO;
import com.iot.core.dto.response.StationVO;
import com.iot.core.service.StationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 用户端充电站/充电桩查询控制器
 * <p>
 * 提供充电站列表、站点详情和充电桩详情查询接口。
 * 所有接口需要 JWT 认证。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    /**
     * 获取充电站列表（分页 + 排序）
     * <p>
     * 支持按名称模糊搜索，按距离/价格/可用桩数排序。
     * 按距离排序时需要提供用户当前经纬度。
     * </p>
     *
     * @param page      页码，默认1
     * @param size      每页数量，默认20
     * @param name      站点名称（模糊搜索），可选
     * @param sortBy    排序方式：distance/price/available，可选
     * @param latitude  用户纬度（按距离排序时必传）
     * @param longitude 用户经度（按距离排序时必传）
     * @return 分页充电站列表
     */
    @GetMapping("/station/list")
    public Result<PageResult<StationVO>> listStations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude) {

        log.info("[站点列表] page: {}, size: {}, name: {}, sortBy: {}, lat: {}, lng: {}",
                page, size, name, sortBy, latitude, longitude);

        PageResult<StationVO> result = stationService.listStations(
                page, size, name, sortBy, latitude, longitude);
        return Result.success(result);
    }

    /**
     * 获取充电站详情（含充电桩列表）
     *
     * @param id 充电站ID
     * @return 站点详情
     */
    @GetMapping("/station/{id}")
    public Result<StationDetailVO> getStationDetail(@PathVariable Long id) {
        log.info("[站点详情] stationId: {}", id);
        StationDetailVO detail = stationService.getStationDetail(id);
        return Result.success(detail);
    }

    /**
     * 获取充电桩详情
     *
     * @param id 充电桩ID
     * @return 充电桩详情
     */
    @GetMapping("/charger/{id}")
    public Result<ChargerVO> getChargerDetail(@PathVariable Long id) {
        log.info("[充电桩详情] chargerId: {}", id);
        ChargerVO charger = stationService.getChargerDetail(id);
        return Result.success(charger);
    }
}
