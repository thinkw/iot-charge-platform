package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 趋势统计 VO
 * <p>
 * 包含订单量、充电量和营收的趋势数据，用于折线图展示。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendVO {

    /**
     * 订单量趋势（每日订单数）
     */
    private List<TrendPoint> orderTrend;

    /**
     * 充电量趋势（每日充电量 kWh）
     */
    private List<TrendPoint> energyTrend;

    /**
     * 营收趋势（每日营收 元）
     */
    private List<TrendPoint> revenueTrend;

    /**
     * 趋势数据点
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {

        /**
         * 日期（格式：yyyy-MM-dd）
         */
        private String date;

        /**
         * 整数值（订单数、设备数等）
         */
        private long value;

        /**
         * 小数值（营收、电量等）
         */
        private BigDecimal decimalValue;
    }
}
