package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 实时数据大屏 VO
 * <p>
 * 运营监控大屏的核心指标数据，包括设备状态、订单和营收概览。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVO {

    /**
     * 当前在线设备数
     */
    private int onlineDeviceCount;

    /**
     * 总设备数
     */
    private int totalDeviceCount;

    /**
     * 设备在线率（百分比，如 85.5）
     */
    private double onlineRate;

    /**
     * 当前充电中的设备数
     */
    private int chargingCount;

    /**
     * 今日订单总数
     */
    private long todayOrderCount;

    /**
     * 今日营收总额（元）
     */
    private BigDecimal todayRevenue;

    /**
     * 未处理告警数
     */
    private int unhandledAlarmCount;
}
