package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.common.exception.BusinessException;
import com.iot.common.model.PageResult;
import com.iot.core.dto.response.AlarmStatisticsVO;
import com.iot.core.entity.Alarm;
import com.iot.core.mapper.AlarmMapper;
import com.iot.core.service.AlarmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 告警服务实现类
 * <p>
 * 提供告警记录的查询、处理、统计具体实现。
 * 告警创建逻辑在 DeviceServiceImpl 中（处理 MQTT 故障上报时创建）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService {

    private final AlarmMapper alarmMapper;

    /** 告警未处理状态 */
    private static final int STATUS_UNHANDLED = 0;
    /** 告警已处理状态 */
    private static final int STATUS_HANDLED = 1;

    // ==================== 查询 ====================

    /**
     * 分页查询告警列表
     * <p>
     * 支持按充电桩、充电站、告警类型、告警级别、状态、时间范围多条件筛选。
     * 结果按创建时间降序排列。
     * </p>
     */
    @Override
    public PageResult<Alarm> listAlarms(Long chargerId, Long stationId, String alarmType,
                                         Integer alarmLevel, Integer status,
                                         LocalDateTime startTime, LocalDateTime endTime,
                                         int page, int size) {
        LambdaQueryWrapper<Alarm> wrapper = new LambdaQueryWrapper<>();

        if (chargerId != null) {
            wrapper.eq(Alarm::getChargerId, chargerId);
        }
        if (stationId != null) {
            wrapper.eq(Alarm::getStationId, stationId);
        }
        if (alarmType != null && !alarmType.isBlank()) {
            wrapper.eq(Alarm::getAlarmType, alarmType);
        }
        if (alarmLevel != null) {
            wrapper.eq(Alarm::getAlarmLevel, alarmLevel);
        }
        if (status != null) {
            wrapper.eq(Alarm::getStatus, status);
        }
        if (startTime != null) {
            wrapper.ge(Alarm::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(Alarm::getCreateTime, endTime);
        }

        // 按创建时间降序，最新的告警在前
        wrapper.orderByDesc(Alarm::getCreateTime);

        Page<Alarm> pageResult = alarmMapper.selectPage(Page.of(page, size), wrapper);
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }

    /**
     * 获取告警详情
     */
    @Override
    public Alarm getAlarmDetail(Long id) {
        Alarm alarm = alarmMapper.selectById(id);
        if (alarm == null) {
            throw new BusinessException(404, "告警记录不存在");
        }
        return alarm;
    }

    // ==================== 处理 ====================

    /**
     * 处理告警
     * <p>
     * 只能处理状态为"未处理"的告警，已处理的告警不允许重复处理。
     * 记录处理人ID、处理时间和处理备注。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAlarm(Long alarmId, Long handlerId, String handleNote) {
        Alarm alarm = alarmMapper.selectById(alarmId);
        if (alarm == null) {
            throw new BusinessException(404, "告警记录不存在");
        }
        if (alarm.getStatus() != STATUS_UNHANDLED) {
            throw new BusinessException(409, "该告警已被处理，请勿重复操作");
        }

        alarm.setStatus(STATUS_HANDLED);
        alarm.setHandlerId(handlerId);
        alarm.setHandleTime(LocalDateTime.now());
        alarm.setHandleNote(handleNote);
        alarmMapper.updateById(alarm);

        log.info("[告警处理] alarmId: {}, handlerId: {}, alarmType: {}, alarmLevel: {}",
                alarmId, handlerId, alarm.getAlarmType(), alarm.getAlarmLevel());
    }

    // ==================== 统计 ====================

    /**
     * 获取告警统计数据
     */
    @Override
    public AlarmStatisticsVO getAlarmStatistics() {
        return AlarmStatisticsVO.builder()
                .unhandledCount(getUnhandledAlarmCount())
                .countByType(getAlarmCountByType())
                .countByLevel(getAlarmCountByLevel())
                .build();
    }

    /**
     * 按告警类型统计未处理告警数量
     * <p>
     * 使用 SQL GROUP BY 聚合查询，避免加载全部未处理告警到内存。
     * </p>
     */
    @Override
    public Map<String, Long> getAlarmCountByType() {
        List<Map<String, Object>> rows = alarmMapper.countUnhandledByType();
        // 使用 LinkedHashMap 保持稳定的迭代顺序
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String alarmType = String.valueOf(row.get("alarm_type"));
            Long cnt = ((Number) row.get("cnt")).longValue();
            result.put(alarmType, cnt);
        }
        return result;
    }

    /**
     * 按告警级别统计未处理告警数量
     */
    @Override
    public Map<String, Long> getAlarmCountByLevel() {
        List<Map<String, Object>> rows = alarmMapper.countUnhandledByLevel();
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String level = String.valueOf(row.get("alarm_level"));
            Long cnt = ((Number) row.get("cnt")).longValue();
            result.put(level, cnt);
        }
        return result;
    }

    /**
     * 获取未处理告警总数
     */
    @Override
    public long getUnhandledAlarmCount() {
        return alarmMapper.selectCount(
                new LambdaQueryWrapper<Alarm>().eq(Alarm::getStatus, STATUS_UNHANDLED)
        );
    }
}
