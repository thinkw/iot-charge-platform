package com.iot.core.service;

import com.iot.common.model.PageResult;
import com.iot.core.dto.response.ChargerVO;
import com.iot.core.dto.response.StationDetailVO;
import com.iot.core.dto.response.StationVO;
import com.iot.core.entity.Station;

import java.math.BigDecimal;

/**
 * 充电站服务接口
 * <p>
 * 提供充电站和充电桩的查询功能（用户端），以及管理端充电站CRUD功能。
 * </p>
 *
 * @author IoT Team
 */
public interface StationService {

    // ==================== 用户端查询 ====================

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

    // ==================== 管理端 CRUD ====================

    /**
     * 管理端分页查询充电站列表
     * <p>
     * 支持按名称模糊搜索和状态筛选。
     * </p>
     *
     * @param name   站点名称（可选，模糊搜索）
     * @param status 状态（可选）
     * @param page   页码
     * @param size   每页数量
     * @return 分页充电站列表
     */
    PageResult<Station> adminListStations(String name, Integer status, int page, int size);

    /**
     * 管理端获取充电站详情
     *
     * @param id 充电站ID
     * @return 充电站信息
     */
    Station adminGetStation(Long id);

    /**
     * 管理端新增充电站
     *
     * @param station 充电站信息
     * @return 新增后的充电站
     */
    Station adminCreateStation(Station station);

    /**
     * 管理端修改充电站
     *
     * @param station 充电站信息
     * @return 修改后的充电站
     */
    Station adminUpdateStation(Station station);

    /**
     * 管理端删除充电站
     * <p>
     * 删除前检查是否有关联的充电桩，有关联则抛出异常。
     * </p>
     *
     * @param id 充电站ID
     */
    void adminDeleteStation(Long id);

    /**
     * 管理端修改充电站营业状态
     *
     * @param id     充电站ID
     * @param status 状态：0-暂停营业，1-营业中，2-维护中
     */
    void adminUpdateStationStatus(Long id, Integer status);
}
