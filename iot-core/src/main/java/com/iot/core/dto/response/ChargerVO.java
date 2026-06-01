package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 充电桩 VO
 * <p>
 * 用于充电站详情页的充电桩列表展示。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargerVO {

    /** 主键ID */
    private Long id;

    /** 设备唯一序列号 */
    private String sn;

    /** 充电桩名称 */
    private String name;

    /** 额定功率(kW) */
    private BigDecimal power;

    /** 状态：0-离线，1-空闲，2-充电中，3-故障，4-锁定 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 当前功率(kW) */
    private BigDecimal currentPower;

    /** 已充电量(kWh) */
    private BigDecimal chargedEnergy;
}
