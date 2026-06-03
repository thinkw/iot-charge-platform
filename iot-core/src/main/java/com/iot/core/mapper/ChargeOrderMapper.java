package com.iot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.core.entity.ChargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 充电订单 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus BaseMapper 获得通用 CRUD 能力。
 * 复杂聚合查询通过 @Select 注解直接编写 SQL。
 * </p>
 *
 * @author IoT Team
 */
@Mapper
public interface ChargeOrderMapper extends BaseMapper<ChargeOrder> {

    /**
     * 按站点聚合已完成订单的统计指标（订单数、总电量、总营收）
     * <p>
     * 使用 SQL GROUP BY 聚合代替 Java 内存聚合，避免加载全部订单到内存。
     * 返回 Map 包含字段：station_id, order_count, total_energy, total_revenue
     * </p>
     *
     * @return 按站点的聚合统计结果列表
     */
    @Select("SELECT station_id, COUNT(*) AS order_count, "
            + "COALESCE(SUM(charged_energy), 0) AS total_energy, "
            + "COALESCE(SUM(total_amount), 0) AS total_revenue "
            + "FROM charge_order WHERE order_status = 2 "
            + "GROUP BY station_id")
    List<Map<String, Object>> selectStationAggregation();
}
