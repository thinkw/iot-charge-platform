package com.iot.api.controller.admin;

import com.iot.api.security.SecurityUtil;
import com.iot.common.model.Result;
import com.iot.core.dto.response.DashboardVO;
import com.iot.core.dto.response.FaultStatisticsVO;
import com.iot.core.dto.response.StationRankVO;
import com.iot.core.dto.response.TrendVO;
import com.iot.core.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 运营后台 - 数据统计控制器
 * <p>
 * 提供实时大屏数据、趋势统计、站点排名和故障统计接口。
 * 所有接口需要 ROLE_ADMIN 权限（由 SecurityConfig 控制）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
public class AdminStatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 获取实时大屏数据
     * <p>
     * 包含：在线设备数/在线率、充电中数量、今日订单数、
     * 今日营收、未处理告警数。
     * </p>
     *
     * @return 大屏数据
     */
    @GetMapping("/dashboard")
    public Result<DashboardVO> getDashboard() {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-实时大屏] 操作人: {}", operatorId);

        DashboardVO dashboard = statisticsService.getDashboard();
        return Result.success(dashboard);
    }

    /**
     * 获取趋势统计数据
     * <p>
     * 按日统计订单量、充电量和营收趋势。
     * 默认查询最近7天数据。
     * </p>
     *
     * @param period    统计周期（day/week/month，当前按日聚合）
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @return 趋势数据
     */
    @GetMapping("/trend")
    public Result<TrendVO> getTrend(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-趋势统计] 操作人: {}, period: {}, {} ~ {}", operatorId, period, startDate, endDate);

        TrendVO trend = statisticsService.getTrend(period, startDate, endDate);
        return Result.success(trend);
    }

    /**
     * 获取站点排名
     * <p>
     * 按指定维度对充电站进行排名。
     * </p>
     *
     * @param type 排名维度：order（订单量）/ energy（充电量）/ revenue（营收）
     * @param topN 返回前N名，默认10
     * @return 站点排名列表
     */
    @GetMapping("/station-rank")
    public Result<List<StationRankVO>> getStationRank(
            @RequestParam(defaultValue = "order") String type,
            @RequestParam(defaultValue = "10") int topN) {

        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-站点排名] 操作人: {}, type: {}, topN: {}", operatorId, type, topN);

        List<StationRankVO> rankings = statisticsService.getStationRank(type, topN);
        return Result.success(rankings);
    }

    /**
     * 获取故障统计数据
     * <p>
     * 包含各类型故障数量分布和总体故障率。
     * </p>
     *
     * @return 故障统计数据
     */
    @GetMapping("/fault")
    public Result<FaultStatisticsVO> getFaultStatistics() {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-故障统计] 操作人: {}", operatorId);

        FaultStatisticsVO statistics = statisticsService.getFaultStatistics();
        return Result.success(statistics);
    }
}
