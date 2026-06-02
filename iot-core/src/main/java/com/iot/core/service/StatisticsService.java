package com.iot.core.service;

import com.iot.core.dto.response.DashboardVO;
import com.iot.core.dto.response.FaultStatisticsVO;
import com.iot.core.dto.response.StationRankVO;
import com.iot.core.dto.response.TrendVO;

import java.util.List;

/**
 * 数据统计服务接口
 * <p>
 * 提供运营大屏实时数据、趋势统计、站点排名和故障统计功能。
 * 所有统计数据来源于 MySQL 聚合查询和 Redis 实时状态。
 * </p>
 *
 * @author IoT Team
 */
public interface StatisticsService {

    /**
     * 获取实时大屏数据
     * <p>
     * 包含：在线设备数/在线率、充电中数量、今日订单数、今日营收、未处理告警数。
     * </p>
     *
     * @return 大屏数据 VO
     */
    DashboardVO getDashboard();

    /**
     * 获取趋势统计数据
     * <p>
     * 按时间范围统计订单量、充电量和营收的每日趋势。
     * </p>
     *
     * @param period    统计周期：day / week / month
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @return 趋势数据 VO
     */
    TrendVO getTrend(String period, String startDate, String endDate);

    /**
     * 获取站点排名
     * <p>
     * 按指定维度（订单量/充电量/营收）对充电站进行排名。
     * </p>
     *
     * @param type 排名维度：order / energy / revenue
     * @param topN 返回前 N 名
     * @return 站点排名列表
     */
    List<StationRankVO> getStationRank(String type, int topN);

    /**
     * 获取故障统计
     * <p>
     * 包含各类型故障数量分布和总体故障率。
     * </p>
     *
     * @return 故障统计 VO
     */
    FaultStatisticsVO getFaultStatistics();
}
