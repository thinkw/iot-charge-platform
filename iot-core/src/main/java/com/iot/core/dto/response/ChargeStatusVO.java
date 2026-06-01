package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充电实时状态 VO
 * <p>
 * 用于用户查看充电进度，包含实时电力数据和费用估算。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeStatusVO {

    /** 订单编号 */
    private String orderNo;

    /** 订单状态 */
    private Integer orderStatus;

    /** 订单状态描述 */
    private String orderStatusDesc;

    /** 充电开始时间 */
    private LocalDateTime startTime;

    /** 当前电压(V) */
    private BigDecimal voltage;

    /** 当前电流(A) */
    private BigDecimal current;

    /** 当前功率(kW) */
    private BigDecimal currentPower;

    /** 已充电量(kWh) */
    private BigDecimal chargedEnergy;

    /** 估算费用(元) */
    private BigDecimal estimatedAmount;

    /** 已充时长(秒) */
    private Long durationSeconds;

    /** 设备温度(℃) */
    private BigDecimal temperature;
}
