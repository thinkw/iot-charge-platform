package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    /** 设备状态 Redis Key 前缀 */
    private static final String DEVICE_STATUS_PREFIX = "device:status:";

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
        // 1. 统计设备在线和充电数量（从 Redis）
        int onlineCount = 0;
        int chargingCount = 0;

        Set<String> keys = redisTemplate.keys(DEVICE_STATUS_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                Object statusObj = redisTemplate.opsForHash().get(key, "status");
                Object onlineObj = redisTemplate.opsForHash().get(key, "online");
                if (onlineObj != null && Integer.parseInt(onlineObj.toString()) == 1) {
                    onlineCount++;
                }
                if (statusObj != null && Integer.parseInt(statusObj.toString()) == 2) {
                    chargingCount++;
                }
            }
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

        return DashboardVO.builder()
                .onlineDeviceCount(onlineCount)
                .totalDeviceCount((int) totalDeviceCount)
                .onlineRate(onlineRate)
                .chargingCount(chargingCount)
                .todayOrderCount(todayOrderCount)
                .todayRevenue(todayRevenue)
                .unhandledAlarmCount(unhandledAlarmCount)
                .build();
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
     * 按指定维度对充电站进行排名：
     * - order：按订单数量降序
     * - energy：按充电总量降序
     * - revenue：按总营收降序
     * </p>
     */
    @Override
    public List<StationRankVO> getStationRank(String type, int topN) {
        // 查询所有已完成订单
        List<ChargeOrder> completedOrders = chargeOrderMapper.selectList(
                new LambdaQueryWrapper<ChargeOrder>()
                        .eq(ChargeOrder::getOrderStatus, 2) // COMPLETED
        );

        // 按站点聚合
        Map<Long, List<ChargeOrder>> ordersByStation = completedOrders.stream()
                .collect(Collectors.groupingBy(ChargeOrder::getStationId));

        // 查询所有站点名称映射
        Map<Long, String> stationNameMap = stationMapper.selectList(null).stream()
                .collect(Collectors.toMap(Station::getId, Station::getName, (a, b) -> a));

        // 构建排名数据
        List<StationRankVO> rankings = new ArrayList<>();
        for (Map.Entry<Long, List<ChargeOrder>> entry : ordersByStation.entrySet()) {
            Long stationId = entry.getKey();
            List<ChargeOrder> stationOrders = entry.getValue();

            BigDecimal totalEnergy = stationOrders.stream()
                    .map(ChargeOrder::getChargedEnergy)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalRevenue = stationOrders.stream()
                    .map(ChargeOrder::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            rankings.add(StationRankVO.builder()
                    .stationId(stationId)
                    .stationName(stationNameMap.getOrDefault(stationId, "未知站点"))
                    .orderCount(stationOrders.size())
                    .totalEnergy(totalEnergy)
                    .totalRevenue(totalRevenue)
                    .build());
        }

        // 按指定维度排序
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
}
