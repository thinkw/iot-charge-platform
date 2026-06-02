package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.common.model.PageResult;
import com.iot.core.entity.OperationLog;
import com.iot.core.mapper.OperationLogMapper;
import com.iot.core.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 操作日志服务实现类
 * <p>
 * 提供操作日志的异步保存和后台分页查询功能。
 * 保存操作使用 @Async 异步执行，不阻塞主业务流程。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    /**
     * 异步保存操作日志
     * <p>
     * 由 LogAspect 切面在接口调用完成后异步调用，
     * 失败仅记录错误日志，不影响业务接口正常返回。
     * </p>
     *
     * @param operationLog 操作日志
     */
    @Override
    @Async
    public void save(OperationLog operationLog) {
        try {
            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            log.error("[操作日志] 保存失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 分页查询操作日志
     * <p>
     * 按创建时间降序排列，支持按用户、模块、时间范围筛选。
     * </p>
     */
    @Override
    public PageResult<OperationLog> list(int page, int size, Long userId, String module,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (module != null && !module.isBlank()) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (startTime != null) {
            wrapper.ge(OperationLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(OperationLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);

        Page<OperationLog> pageResult = operationLogMapper.selectPage(Page.of(page, size), wrapper);
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }
}
