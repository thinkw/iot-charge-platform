package com.iot.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.iot.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充电桩实体
 *
 * @author IoT Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("charger")
public class Charger extends BaseEntity {

    /**
     * 设备唯一序列号
     */
    private String sn;

    /**
     * 充电桩名称
     */
    private String name;

    /**
     * 所属充电站ID
     */
    private Long stationId;

    /**
     * 额定功率(kW)
     */
    private BigDecimal power;

    /**
     * 状态：0-离线，1-空闲，2-充电中，3-故障，4-锁定
     */
    private Integer status;

    /**
     * 当前电压(V)
     */
    private BigDecimal currentVoltage;

    /**
     * 当前电流(A)
     */
    private BigDecimal currentCurrent;

    /**
     * 当前功率(kW)
     */
    private BigDecimal currentPower;

    /**
     * 已充电量(kWh)
     */
    private BigDecimal chargedEnergy;

    /**
     * 设备温度(℃)
     */
    private BigDecimal temperature;

    /**
     * 最后上线时间
     */
    private LocalDateTime lastOnlineTime;
}
