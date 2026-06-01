package com.iot.core.mapper;

import com.iot.core.entity.ChargeOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 充电订单 Mapper 接口
 *
 * @author IoT Team
 */
@Mapper
public interface ChargeOrderMapper extends BaseMapper<ChargeOrder> {
}
