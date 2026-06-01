package com.iot.core.mapper;

import com.iot.core.entity.Station;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 充电站 Mapper 接口
 *
 * @author IoT Team
 */
@Mapper
public interface StationMapper extends BaseMapper<Station> {
}
