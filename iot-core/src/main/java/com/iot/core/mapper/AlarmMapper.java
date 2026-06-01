package com.iot.core.mapper;

import com.iot.core.entity.Alarm;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警记录 Mapper 接口
 *
 * @author IoT Team
 */
@Mapper
public interface AlarmMapper extends BaseMapper<Alarm> {
}
