package com.iot.core.service;

import com.iot.common.model.PageResult;
import com.iot.core.entity.Charger;

/**
 * 充电桩管理服务接口
 * <p>
 * 提供运营后台对充电桩的增删改查和状态管理功能。
 * </p>
 *
 * @author IoT Team
 */
public interface ChargerService {

    /**
     * 分页查询充电桩列表
     * <p>
     * 支持按充电站、SN、状态筛选。
     * </p>
     *
     * @param stationId 充电站ID（可选）
     * @param sn        设备SN模糊搜索（可选）
     * @param status    状态筛选（可选）
     * @param page      页码
     * @param size      每页数量
     * @return 分页充电桩列表
     */
    PageResult<Charger> adminListChargers(Long stationId, String sn, Integer status,
                                           int page, int size);

    /**
     * 获取充电桩详情
     *
     * @param id 充电桩ID
     * @return 充电桩信息
     */
    Charger adminGetCharger(Long id);

    /**
     * 新增充电桩
     * <p>
     * 校验 SN 唯一性，重复则抛出异常。
     * </p>
     *
     * @param charger 充电桩信息
     * @return 新增后的充电桩（含自动生成的ID）
     */
    Charger adminCreateCharger(Charger charger);

    /**
     * 修改充电桩
     * <p>
     * SN 和 stationId 不允许修改。
     * </p>
     *
     * @param charger 充电桩信息（含要修改的字段）
     * @return 修改后的充电桩
     */
    Charger adminUpdateCharger(Charger charger);

    /**
     * 删除充电桩
     * <p>
     * 删除前检查是否有进行中的充电订单，有关联则不允许删除。
     * </p>
     *
     * @param id 充电桩ID
     */
    void adminDeleteCharger(Long id);

    /**
     * 修改充电桩启禁用状态
     * <p>
     * 禁用后将充电桩状态设为离线，启用后设为空闲。
     * 同时更新 Redis 缓存。
     * </p>
     *
     * @param id     充电桩ID
     * @param status 状态：0-禁用，1-启用
     */
    void adminUpdateChargerStatus(Long id, Integer status);
}
