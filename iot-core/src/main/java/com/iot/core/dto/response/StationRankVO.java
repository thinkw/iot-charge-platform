package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 站点排名 VO
 * <p>
 * 按订单量、充电量或营收对充电站进行排名展示。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationRankVO {

    /**
     * 充电站ID
     */
    private Long stationId;

    /**
     * 充电站名称
     */
    private String stationName;

    /**
     * 订单总数
     */
    private long orderCount;

    /**
     * 总充电量（kWh）
     */
    private BigDecimal totalEnergy;

    /**
     * 总营收（元）
     */
    private BigDecimal totalRevenue;
}
