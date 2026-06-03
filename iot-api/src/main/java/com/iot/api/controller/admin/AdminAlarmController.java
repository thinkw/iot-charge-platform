package com.iot.api.controller.admin;

import com.iot.api.security.SecurityUtil;
import com.iot.common.model.PageResult;
import com.iot.common.model.Result;
import com.iot.common.util.DateTimeUtil;
import com.iot.core.dto.request.AlarmHandleRequest;
import com.iot.core.dto.response.AlarmStatisticsVO;
import com.iot.core.entity.Alarm;
import com.iot.core.service.AlarmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 运营后台 - 告警管理控制器
 * <p>
 * 提供告警列表查询、详情查看、处理告警和告警统计接口。
 * 所有接口需要 ROLE_ADMIN 权限（由 SecurityConfig 控制）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/alarm")
@RequiredArgsConstructor
public class AdminAlarmController {

    private final AlarmService alarmService;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分页查询告警列表
     * <p>
     * 支持按充电桩、充电站、告警类型、告警级别、状态、时间范围筛选。
     * </p>
     *
     * @param page       页码，默认1
     * @param size       每页数量，默认20
     * @param chargerId  充电桩ID（可选）
     * @param stationId  充电站ID（可选）
     * @param alarmType  告警类型（可选）
     * @param alarmLevel 告警级别（可选）：1-一般，2-重要，3-紧急
     * @param status     处理状态（可选）：0-未处理，1-已处理
     * @param startTime  开始时间（可选）
     * @param endTime    结束时间（可选）
     * @return 分页告警列表
     */
    @GetMapping("/list")
    public Result<PageResult<Alarm>> listAlarms(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long chargerId,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) String alarmType,
            @RequestParam(required = false) Integer alarmLevel,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-告警列表] 操作人: {}, chargerId: {}, alarmType: {}, alarmLevel: {}, status: {}",
                operatorId, chargerId, alarmType, alarmLevel, status);

        LocalDateTime start = DateTimeUtil.parseControllerParam(startTime);
        LocalDateTime end = DateTimeUtil.parseControllerParam(endTime);

        PageResult<Alarm> result = alarmService.listAlarms(
                chargerId, stationId, alarmType, alarmLevel, status, start, end, page, size);
        return Result.success(result);
    }

    /**
     * 获取告警详情
     *
     * @param id 告警ID
     * @return 告警详情
     */
    @GetMapping("/{id}")
    public Result<Alarm> getAlarmDetail(@PathVariable Long id) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-告警详情] 操作人: {}, alarmId: {}", operatorId, id);

        Alarm alarm = alarmService.getAlarmDetail(id);
        return Result.success(alarm);
    }

    /**
     * 处理告警
     * <p>
     * 将告警标记为"已处理"，记录处理人和处理备注。
     * 已处理的告警不允许重复处理。
     * </p>
     *
     * @param request 处理请求（alarmId, handleNote）
     * @return 操作结果
     */
    @PostMapping("/handle")
    public Result<String> handleAlarm(@RequestBody @Valid AlarmHandleRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-处理告警] 操作人: {}, alarmId: {}, note: {}", operatorId,
                request.getAlarmId(), request.getHandleNote());

        alarmService.handleAlarm(request.getAlarmId(), operatorId, request.getHandleNote());
        return Result.success("告警已处理");
    }

    /**
     * 获取告警统计数据
     * <p>
     * 包含未处理告警数、按类型分布、按级别分布。
     * </p>
     *
     * @return 告警统计数据
     */
    @GetMapping("/statistics")
    public Result<AlarmStatisticsVO> getAlarmStatistics() {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-告警统计] 操作人: {}", operatorId);

        AlarmStatisticsVO statistics = alarmService.getAlarmStatistics();
        return Result.success(statistics);
    }
}
