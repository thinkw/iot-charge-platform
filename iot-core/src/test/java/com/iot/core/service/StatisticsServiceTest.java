package com.iot.core.service;

import com.iot.core.dto.response.DashboardVO;
import com.iot.core.dto.response.FaultStatisticsVO;
import com.iot.core.dto.response.StationRankVO;
import com.iot.core.mapper.ChargeOrderMapper;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mapper.StationMapper;
import com.iot.core.service.impl.StatisticsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StatisticsServiceImpl 单元测试
 * <p>
 * 覆盖大屏数据、站点排名、故障统计的核心逻辑。
 * 验证 Dashboard 缓存和 SCAN 替换 KEYS 的正确性。
 * </p>
 *
 * @author IoT Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StatisticsServiceImpl 单元测试")
class StatisticsServiceTest {

    @Mock private ChargerMapper chargerMapper;
    @Mock private StationMapper stationMapper;
    @Mock private ChargeOrderMapper chargeOrderMapper;
    @Mock private AlarmService alarmService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private Cursor<String> cursor;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    // ==================== getDashboard 测试 ====================

    @Test
    @DisplayName("getDashboard - 正常统计（SCAN 方式）")
    void getDashboard_Normal() {
        // 模拟 SCAN 返回 3 个设备 key
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, true, false);
        when(cursor.next())
                .thenReturn("device:status:CHARGER-001")
                .thenReturn("device:status:CHARGER-002")
                .thenReturn("device:status:CHARGER-003");
        when(hashOperations.get("device:status:CHARGER-001", "online")).thenReturn("1");
        when(hashOperations.get("device:status:CHARGER-001", "status")).thenReturn("1");
        when(hashOperations.get("device:status:CHARGER-002", "online")).thenReturn("1");
        when(hashOperations.get("device:status:CHARGER-002", "status")).thenReturn("2");
        when(hashOperations.get("device:status:CHARGER-003", "online")).thenReturn("0");
        when(hashOperations.get("device:status:CHARGER-003", "status")).thenReturn("0");

        when(chargerMapper.selectCount(null)).thenReturn(100L);
        when(chargeOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(alarmService.getUnhandledAlarmCount()).thenReturn(5L);

        DashboardVO dashboard = statisticsService.getDashboard();

        assertEquals(2, dashboard.getOnlineDeviceCount());
        assertEquals(100, dashboard.getTotalDeviceCount());
        assertEquals(2.0, dashboard.getOnlineRate(), 0.01);
        assertEquals(1, dashboard.getChargingCount());
        assertEquals(0, dashboard.getTodayOrderCount());
        assertEquals(BigDecimal.ZERO.compareTo(dashboard.getTodayRevenue()), 0, "今日营收应为 0.00");
        assertEquals(5, dashboard.getUnhandledAlarmCount());
    }

    @Test
    @DisplayName("getDashboard - 使用缓存（DashboardPushScheduler 高频轮询场景）")
    void getDashboard_UsesCache() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);
        when(chargerMapper.selectCount(null)).thenReturn(100L);
        when(chargeOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(alarmService.getUnhandledAlarmCount()).thenReturn(0L);

        DashboardVO first = statisticsService.getDashboard();
        assertNotNull(first);

        // 二次调用应命中缓存（不再 SCAN）
        DashboardVO second = statisticsService.getDashboard();
        assertSame(first, second, "高频轮询应命中缓存");
        verify(redisTemplate, times(1)).scan(any(ScanOptions.class));
    }

    // ==================== getStationRank 测试 ====================

    @Test
    @DisplayName("getStationRank - 使用 SQL 聚合")
    void getStationRank_UsesSqlAggregation() {
        Map<String, Object> row = new HashMap<>();
        row.put("station_id", 1L);
        row.put("order_count", 50L);
        row.put("total_energy", new BigDecimal("125.50"));
        row.put("total_revenue", new BigDecimal("150.00"));

        when(chargeOrderMapper.selectStationAggregation()).thenReturn(Collections.singletonList(row));
        when(stationMapper.selectList(null)).thenReturn(Collections.emptyList());

        List<StationRankVO> rankings = statisticsService.getStationRank("order", 10);

        assertEquals(1, rankings.size());
        assertEquals(50L, rankings.get(0).getOrderCount());
    }

    // ==================== getFaultStatistics 测试 ====================

    @Test
    @DisplayName("getFaultStatistics - 正常统计")
    void getFaultStatistics_Normal() {
        Map<String, Long> countByType = new LinkedHashMap<>();
        countByType.put("OVER_TEMP", 3L);
        countByType.put("OFFLINE", 2L);
        when(alarmService.getAlarmCountByType()).thenReturn(countByType);
        when(chargerMapper.selectCount(null)).thenReturn(100L);

        FaultStatisticsVO stats = statisticsService.getFaultStatistics();

        assertEquals(5, stats.getTotalFaultCount());
        assertEquals(5.0, stats.getFaultRate(), 0.01);
        assertEquals(2, stats.getFaultCountByType().size());
    }
}
