package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 告警统计 VO
 * <p>
 * 用于运营后台告警模块的统计数据展示。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmStatisticsVO {

    /**
     * 未处理告警总数
     */
    private long unhandledCount;

    /**
     * 按告警类型统计数量
     * key: alarmType (OVER_TEMP/OVER_VOLT/OFFLINE等)
     * value: 数量
     */
    private Map<String, Long> countByType;

    /**
     * 按告警级别统计数量
     * key: alarmLevel (1-一般/2-重要/3-紧急)
     * value: 数量
     */
    private Map<String, Long> countByLevel;
}
