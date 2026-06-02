package com.iot.core.service;

import com.iot.common.model.PageResult;
import com.iot.core.dto.response.AlarmStatisticsVO;
import com.iot.core.entity.Alarm;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 告警服务接口
 * <p>
 * 提供告警记录的查询、处理、统计功能，供运营后台使用。
 * </p>
 *
 * @author IoT Team
 */
public interface AlarmService {

    /**
     * 分页查询告警列表
     * <p>
     * 支持按充电桩、充电站、告警类型、告警级别、处理状态、时间范围多条件筛选。
     * 结果按创建时间降序排列（最新的告警在前）。
     * </p>
     *
     * @param chargerId  充电桩ID（可选）
     * @param stationId  充电站ID（可选）
     * @param alarmType  告警类型（可选）
     * @param alarmLevel 告警级别（可选）
     * @param status     处理状态（可选）：0-未处理，1-已处理
     * @param startTime  开始时间（可选）
     * @param endTime    结束时间（可选）
     * @param page       页码
     * @param size       每页数量
     * @return 分页告警列表
     */
    PageResult<Alarm> listAlarms(Long chargerId, Long stationId, String alarmType,
                                  Integer alarmLevel, Integer status,
                                  LocalDateTime startTime, LocalDateTime endTime,
                                  int page, int size);

    /**
     * 获取告警详情
     *
     * @param id 告警ID
     * @return 告警详情
     */
    Alarm getAlarmDetail(Long id);

    /**
     * 处理告警
     * <p>
     * 将告警标记为"已处理"，记录处理人、处理时间和处理备注。
     * 只能处理状态为"未处理"的告警。
     * </p>
     *
     * @param alarmId    告警ID
     * @param handlerId  处理人ID（运营管理员）
     * @param handleNote 处理备注
     */
    void handleAlarm(Long alarmId, Long handlerId, String handleNote);

    /**
     * 获取告警统计数据
     * <p>
     * 包括未处理告警数、按类型分布、按级别分布。
     * </p>
     *
     * @return 告警统计VO
     */
    AlarmStatisticsVO getAlarmStatistics();

    /**
     * 按类型统计告警数量（当前未处理的）
     *
     * @return 告警类型 → 数量
     */
    Map<String, Long> getAlarmCountByType();

    /**
     * 按级别统计告警数量（当前未处理的）
     *
     * @return 告警级别 → 数量
     */
    Map<String, Long> getAlarmCountByLevel();

    /**
     * 获取未处理告警数量
     *
     * @return 未处理告警数
     */
    long getUnhandledAlarmCount();
}
