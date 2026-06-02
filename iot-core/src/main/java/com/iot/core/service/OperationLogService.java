package com.iot.core.service;

import com.iot.common.model.PageResult;
import com.iot.core.entity.OperationLog;

import java.time.LocalDateTime;

/**
 * 操作日志服务接口
 * <p>
 * 提供操作日志的异步保存和后台查询功能。
 * </p>
 *
 * @author IoT Team
 */
public interface OperationLogService {

    /**
     * 异步保存操作日志
     * <p>
     * 由 LogAspect 切面调用，不阻塞主业务流程。
     * 失败不影响接口正常返回。
     * </p>
     *
     * @param operationLog 操作日志
     */
    void save(OperationLog operationLog);

    /**
     * 分页查询操作日志
     * <p>
     * 供运营后台查询操作审计记录。
     * </p>
     *
     * @param page      页码
     * @param size      每页数量
     * @param userId    操作用户ID（可选）
     * @param module    操作模块（可选）
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 分页操作日志列表
     */
    PageResult<OperationLog> list(int page, int size, Long userId, String module,
                                   LocalDateTime startTime, LocalDateTime endTime);
}
