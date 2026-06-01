package com.iot.core.service;

import com.iot.common.model.PageResult;
import com.iot.core.dto.response.ChargerVO;
import com.iot.core.dto.response.StationDetailVO;
import com.iot.core.dto.response.StationVO;

import java.math.BigDecimal;

/**
 * 充电站服务接口
 * <p>
 * 提供充电站和充电桩的查询功能，支持分页、排序和距离计算。
 * </p>
 *
 * @author IoT Team
 */
public interface StationService {

    /**
     * 获取充电站列表（分页 + 排序）
     * <p>
     * 支持按距离（提供经纬度时）、价格（最低电价）、可用桩数量排序。
     * 支持按名称模糊搜索。
     * </p>
     *
     * @param page     页码，从1开始
     * @param size     每页数量
     * @param name     站点名称（模糊搜索），可为 null
     * @param sortBy   排序方式：distance / price / available，可为 null（默认按ID排序）
     * @param latitude  用户纬度，按距离排序时必传
     * @param longitude 用户经度，按距离排序时必传
     * @return 分页结果
     */
    PageResult<StationVO> listStations(int page, int size, String name, String sortBy,
                                       BigDecimal latitude, BigDecimal longitude);

    /**
     * 获取充电站详情及充电桩列表
     *
     * @param stationId 充电站ID
     * @return 站点详情（含充电桩列表）
     */
    StationDetailVO getStationDetail(Long stationId);

    /**
     * 获取充电桩详情
     *
     * @param chargerId 充电桩ID
     * @return 充电桩信息
     */
    ChargerVO getChargerDetail(Long chargerId);
}
