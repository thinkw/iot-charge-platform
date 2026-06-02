package com.iot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.core.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper
 *
 * @author IoT Team
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
