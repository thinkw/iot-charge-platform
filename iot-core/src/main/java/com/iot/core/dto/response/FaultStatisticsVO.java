package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 故障统计 VO
 * <p>
 * 设备故障类型分布和故障率统计数据。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaultStatisticsVO {

    /**
     * 按故障类型统计数量
     * key: alarmType (OVER_TEMP/OVER_VOLT/OFFLINE等)
     * value: 数量
     */
    private Map<String, Long> faultCountByType;

    /**
     * 故障率（百分比，故障设备数/总设备数 × 100）
     */
    private double faultRate;

    /**
     * 总故障次数
     */
    private long totalFaultCount;
}
