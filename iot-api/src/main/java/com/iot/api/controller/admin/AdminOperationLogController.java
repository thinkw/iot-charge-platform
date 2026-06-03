package com.iot.api.controller.admin;

import com.iot.common.model.PageResult;
import com.iot.common.model.Result;
import com.iot.common.util.DateTimeUtil;
import com.iot.core.entity.OperationLog;
import com.iot.core.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 管理端操作日志控制器
 * <p>
 * 提供运营后台的操作日志查询功能，支持按操作人、操作模块和时间范围筛选。
 * 对应权限码：admin:log（在 init-data.sql 中已预置）
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/operation-log")
@RequiredArgsConstructor
public class AdminOperationLogController {

    private final OperationLogService operationLogService;

    /**
     * 分页查询操作日志
     * <p>
     * 支持按操作人ID、操作模块、时间范围多条件筛选。
     * 结果按创建时间降序排列（最新的日志在前）。
     * </p>
     *
     * @param page      当前页码（默认1）
     * @param size      每页条数（默认20）
     * @param userId    操作人ID（可选）
     * @param module    操作模块（可选，如"充电站管理"、"订单管理"）
     * @param startTime 开始时间（可选，格式 yyyy-MM-dd HH:mm:ss 或 ISO格式）
     * @param endTime   结束时间（可选）
     * @return 分页操作日志列表
     */
    @GetMapping("/list")
    public Result<PageResult<OperationLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        LocalDateTime start = DateTimeUtil.parseControllerParam(startTime);
        LocalDateTime end = DateTimeUtil.parseControllerParam(endTime);

        PageResult<OperationLog> result = operationLogService.list(page, size, userId, module, start, end);
        return Result.success(result);
    }
}
