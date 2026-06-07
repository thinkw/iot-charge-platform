
package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.common.constant.DeviceConstants;
import com.iot.common.exception.BusinessException;
import com.iot.common.model.PageResult;
import com.iot.core.dto.response.*;
import com.iot.core.entity.ChargeOrder;
import com.iot.core.entity.Charger;
import com.iot.core.entity.Station;
import com.iot.core.mapper.ChargeOrderMapper;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mapper.StationMapper;
import com.iot.core.service.AlarmService;
import com.iot.core.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据统计服务实现类
 * <p>
 * 提供运营大屏实时数据、趋势统计、站点排名和故障统计的具体实现。
 * 数据来源：MySQL 聚合查询 + Redis 实时状态。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final ChargerMapper chargerMapper;
    private final StationMapper stationMapper;
    private final ChargeOrderMapper chargeOrderMapper;
    private final AlarmService alarmService;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Redis Key 常量统一使用 DeviceConstants */

    /** Dashboard 本地缓存（DashboardPushScheduler 每 5 秒轮询，避免重复 SCAN+MySQL） */
    private volatile DashboardVO cachedDashboard;
    private volatile long dashboardCacheTime = 0;
    /** Dashboard 缓存有效期（毫秒），1 秒内重复请求复用缓存，兼顾实时性和性能 */
    private static final long DASHBOARD_CACHE_TTL_MS = 1_000;

    // ==================== 大屏数据 ====================

    /**
     * 获取实时大屏数据
     * <p>
     * 在线设备数和充电中数量从 Redis 实时获取；
     * 今日订单数和营收从 MySQL 聚合查询获取。
     * </p>
     */
    @Override
    public DashboardVO getDashboard() {
        // 优先返回缓存（DashboardPushScheduler 每 5 秒轮询，缓存减少 SCAN + MySQL 开销）
        long now = System.currentTimeMillis();
        if (cachedDashboard != null && (now - dashboardCacheTime) < DASHBOARD_CACHE_TTL_MS) {
            return cachedDashboard;
        }

        // 1. 统计设备在线和充电数量（从 Redis）
        int onlineCount = 0;
        int chargingCount = 0;

        // 使用 SCAN 替代 KEYS，避免阻塞 Redis（生产环境安全）
        try (Cursor<String> cursor = scanDeviceStatusKeys()) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Object statusObj = redisTemplate.opsForHash().get(key, "status");
                Object onlineObj = redisTemplate.opsForHash().get(key, "online");
                if (onlineObj != null && Integer.parseInt(onlineObj.toString()) == 1) {
                    onlineCount++;
                }
                if (statusObj != null && Integer.parseInt(statusObj.toString()) == 2) {
                    chargingCount++;
                }
            }
        } catch (Exception e) {
            log.warn("[大屏数据] Redis SCAN 失败，设备在线统计可能不准确", e);
        }

        // 2. 查询总设备数
        long totalDeviceCount = chargerMapper.selectCount(null);

        // 3. 计算在线率
        double onlineRate = totalDeviceCount > 0
                ? Math.round(onlineCount * 10000.0 / totalDeviceCount) / 100.0
                : 0.0;

        // 4. 今日订单数和营收
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        List<ChargeOrder> todayOrders = chargeOrderMapper.selectList(
                new LambdaQueryWrapper<ChargeOrder>()
                        .ge(ChargeOrder::getCreateTime, todayStart)
                        .le(ChargeOrder::getCreateTime, todayEnd)
        );

        long todayOrderCount = todayOrders.size();
        BigDecimal todayRevenue = todayOrders.stream()
                .map(ChargeOrder::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // 5. 未处理告警数
        int unhandledAlarmCount = (int) alarmService.getUnhandledAlarmCount();

        DashboardVO result = DashboardVO.builder()
                .onlineDeviceCount(onlineCount)
                .totalDeviceCount((int) totalDeviceCount)
                .onlineRate(onlineRate)
                .chargingCount(chargingCount)
                .todayOrderCount(todayOrderCount)
                .todayRevenue(todayRevenue)
                .unhandledAlarmCount(unhandledAlarmCount)
                .build();

        // 更新本地缓存
        cachedDashboard = result;
        dashboardCacheTime = now;

        return result;
    }

    // ==================== 趋势统计 ====================

    /**
     * 获取趋势统计数据
     * <p>
     * 按日统计订单量、充电量和营收。
     * 支持 day/week/month 三种统计周期，默认按日聚合。
     * </p>
     */
    @Override
    public TrendVO getTrend(String period, String startDate, String endDate) {
        // 解析日期范围
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(7);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        // 查询时间范围内的所有订单
        List<ChargeOrder> orders = chargeOrderMapper.selectList(
                new LambdaQueryWrapper<ChargeOrder>()
                        .ge(ChargeOrder::getCreateTime, startDateTime)
                        .le(ChargeOrder::getCreateTime, endDateTime)
                        .orderByAsc(ChargeOrder::getCreateTime)
        );

        // 按日期分组聚合
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, List<ChargeOrder>> ordersByDate = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getCreateTime().format(dateFmt)));

        // 构建趋势数据点
        List<TrendVO.TrendPoint> orderTrend = new ArrayList<>();
        List<TrendVO.TrendPoint> energyTrend = new ArrayList<>();
        List<TrendVO.TrendPoint> revenueTrend = new ArrayList<>();

        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            String dateStr = cursor.format(dateFmt);
            List<ChargeOrder> dayOrders = ordersByDate.getOrDefault(dateStr, Collections.emptyList());

            long orderCount = dayOrders.size();
            BigDecimal dayEnergy = dayOrders.stream()
                    .map(ChargeOrder::getChargedEnergy)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal dayRevenue = dayOrders.stream()
                    .map(ChargeOrder::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            orderTrend.add(TrendVO.TrendPoint.builder().date(dateStr).value(orderCount).build());
            energyTrend.add(TrendVO.TrendPoint.builder().date(dateStr).decimalValue(dayEnergy).build());
            revenueTrend.add(TrendVO.TrendPoint.builder().date(dateStr).decimalValue(dayRevenue).build());

            cursor = cursor.plusDays(1);
        }

        return TrendVO.builder()
                .orderTrend(orderTrend)
                .energyTrend(energyTrend)
                .revenueTrend(revenueTrend)
                .build();
    }

    // ==================== 站点排名 ====================

    /**
     * 获取站点排名
     * <p>
     * 使用 SQL GROUP BY 聚合代替加载全部订单到内存。
     * 按指定维度排序：order=订单量, energy=充电量, revenue=营收。
     * </p>
     */
    @Override
    public List<StationRankVO> getStationRank(String type, int topN) {
        // 使用 SQL 聚合查询（按站点 GROUP BY 已完成订单）
        List<Map<String, Object>> rows = chargeOrderMapper.selectStationAggregation();

        // 查询所有站点名称映射（单次查询）
        Map<Long, String> stationNameMap = stationMapper.selectList(null).stream()
                .collect(Collectors.toMap(Station::getId, Station::getName, (a, b) -> a));

        // 构建排名数据
        List<StationRankVO> rankings = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long stationId = ((Number) row.get("station_id")).longValue();
            Long orderCount = ((Number) row.get("order_count")).longValue();
            BigDecimal totalEnergy = new BigDecimal(row.get("total_energy").toString())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalRevenue = new BigDecimal(row.get("total_revenue").toString())
                    .setScale(2, RoundingMode.HALF_UP);

            rankings.add(StationRankVO.builder()
                    .stationId(stationId)
                    .stationName(stationNameMap.getOrDefault(stationId, "未知站点"))
                    .orderCount(orderCount)
                    .totalEnergy(totalEnergy)
                    .totalRevenue(totalRevenue)
                    .build());
        }

        // 按指定维度降序排序
        switch (type) {
            case "energy" -> rankings.sort((a, b) -> b.getTotalEnergy().compareTo(a.getTotalEnergy()));
            case "revenue" -> rankings.sort((a, b) -> b.getTotalRevenue().compareTo(a.getTotalRevenue()));
            default -> rankings.sort((a, b) -> Long.compare(b.getOrderCount(), a.getOrderCount()));
        }

        // 取前 topN
        return rankings.stream().limit(topN).collect(Collectors.toList());
    }

    // ==================== 故障统计 ====================

    /**
     * 获取故障统计
     * <p>
     * 统计各故障类型的数量和总体故障率。
     * </p>
     */
    @Override
    public FaultStatisticsVO getFaultStatistics() {
        Map<String, Long> faultCountByType = alarmService.getAlarmCountByType();

        long totalFaultCount = faultCountByType.values().stream()
                .mapToLong(Long::longValue).sum();

        long totalDeviceCount = chargerMapper.selectCount(null);
        double faultRate = totalDeviceCount > 0
                ? Math.round(totalFaultCount * 10000.0 / totalDeviceCount) / 100.0
                : 0.0;

        return FaultStatisticsVO.builder()
                .faultCountByType(faultCountByType)
                .faultRate(faultRate)
                .totalFaultCount(totalFaultCount)
                .build();
    }

    /**
     * 使用 Redis SCAN 命令扫描所有设备状态 Key
     * <p>
     * SCAN 命令基于游标迭代遍历，不会像 KEYS 命令一样阻塞 Redis 服务器。
     * 每次 SCAN 返回一批 key，通过 Cursor 统一迭代。
     * </p>
     *
     * @return 设备状态 Key 的游标
     */
    private Cursor<String> scanDeviceStatusKeys() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(DeviceConstants.REDIS_KEY_DEVICE_STATUS + "*")
                .count(100)
                .build();
        return redisTemplate.scan(options);
    }
}
