package com.iot.core.mapper;

import com.iot.core.entity.ReservationOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预约订单 Mapper 接口
 *
 * @author IoT Team
 */
@Mapper
public interface ReservationOrderMapper extends BaseMapper<ReservationOrder> {
}
