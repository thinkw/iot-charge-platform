package com.iot.core.service;

import com.iot.common.exception.BusinessException;
import com.iot.common.model.PageResult;
import com.iot.core.dto.response.AlarmStatisticsVO;
import com.iot.core.entity.Alarm;
import com.iot.core.mapper.AlarmMapper;
import com.iot.core.service.impl.AlarmServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AlarmServiceImpl 单元测试
 * <p>
 * 覆盖告警查询、处理、统计的核心逻辑，验证 SQL GROUP BY 聚合正确性。
 * </p>
 *
 * @author IoT Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlarmServiceImpl 单元测试")
class AlarmServiceTest {

    @Mock
    private AlarmMapper alarmMapper;

    @InjectMocks
    private AlarmServiceImpl alarmService;

    // ==================== handleAlarm 测试 ====================

    @Test
    @DisplayName("handleAlarm - 正常处理告警")
    void handleAlarm_Success() {
        Alarm alarm = new Alarm();
        alarm.setId(1L);
        alarm.setStatus(0); // 未处理

        when(alarmMapper.selectById(1L)).thenReturn(alarm);
        when(alarmMapper.updateById(any(Alarm.class))).thenReturn(1);

        alarmService.handleAlarm(1L, 3L, "已派维修人员处理");

        assertEquals(1, alarm.getStatus(), "处理后状态应为 1(已处理)");
        assertEquals(3L, alarm.getHandlerId(), "处理人ID应为 3");
        assertNotNull(alarm.getHandleTime(), "处理时间不应为空");
        assertEquals("已派维修人员处理", alarm.getHandleNote());
        verify(alarmMapper).updateById(alarm);
    }

    @Test
    @DisplayName("handleAlarm - 告警不存在 → 抛出 BusinessException(404)")
    void handleAlarm_NotFound() {
        when(alarmMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> alarmService.handleAlarm(999L, 3L, "备注"),
                "不存在的告警应抛出异常");
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("handleAlarm - 重复处理 → 抛出 BusinessException(409)")
    void handleAlarm_AlreadyHandled() {
        Alarm alarm = new Alarm();
        alarm.setId(1L);
        alarm.setStatus(1); // 已处理

        when(alarmMapper.selectById(1L)).thenReturn(alarm);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> alarmService.handleAlarm(1L, 3L, "备注"),
                "重复处理应抛出异常");
        assertEquals(409, ex.getCode());
    }

    // ==================== 统计测试（验证 SQL GROUP BY 聚合） ====================

    @Test
    @DisplayName("getAlarmCountByType - 使用 SQL GROUP BY 聚合")
    void getAlarmCountByType_UsesSqlAggregation() {
        List<Map<String, Object>> rows = Arrays.asList(
                createRow("OVER_TEMP", 3L),
                createRow("OFFLINE", 5L),
                createRow("OVER_VOLT", 1L)
        );
        when(alarmMapper.countUnhandledByType()).thenReturn(rows);

        Map<String, Long> result = alarmService.getAlarmCountByType();

        assertEquals(3, result.size());
        assertEquals(3L, result.get("OVER_TEMP"));
        assertEquals(5L, result.get("OFFLINE"));
        assertEquals(1L, result.get("OVER_VOLT"));
        verify(alarmMapper).countUnhandledByType();
        verify(alarmMapper, never()).selectList(any()); // 不应使用内存聚合
    }

    @Test
    @DisplayName("getAlarmCountByLevel - 使用 SQL GROUP BY 聚合")
    void getAlarmCountByLevel_UsesSqlAggregation() {
        List<Map<String, Object>> rows = Arrays.asList(
                createRow("1", 2L),
                createRow("2", 6L),
                createRow("3", 1L)
        );
        when(alarmMapper.countUnhandledByLevel()).thenReturn(rows);

        Map<String, Long> result = alarmService.getAlarmCountByLevel();

        assertEquals(3, result.size());
        assertEquals(2L, result.get("1"));
        assertEquals(6L, result.get("2"));
        assertEquals(1L, result.get("3"));
        verify(alarmMapper).countUnhandledByLevel();
    }

    @Test
    @DisplayName("getAlarmStatistics - 聚合无告警时返回空")
    void getAlarmStatistics_Empty() {
        when(alarmMapper.countUnhandledByType()).thenReturn(Collections.emptyList());
        when(alarmMapper.countUnhandledByLevel()).thenReturn(Collections.emptyList());
        when(alarmMapper.selectCount(any())).thenReturn(0L);

        AlarmStatisticsVO stats = alarmService.getAlarmStatistics();

        assertEquals(0L, stats.getUnhandledCount());
        assertTrue(stats.getCountByType().isEmpty());
        assertTrue(stats.getCountByLevel().isEmpty());
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> createRow(String key, Long count) {
        Map<String, Object> row = new HashMap<>();
        // key 字段名因 SQL SELECT 的别名而异，此处模拟通用格式
        row.put(key.matches("\\d+") ? "alarm_level" : "alarm_type", key);
        row.put("cnt", count);
        return row;
    }
}
