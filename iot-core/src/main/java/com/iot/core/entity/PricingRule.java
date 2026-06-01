package com.iot.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.iot.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 计费规则实体
 *
 * @author IoT Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pricing_rule")
public class PricingRule extends BaseEntity {

    /**
     * 规则名称
     */
    private String name;

    /**
     * 所属充电站ID(0表示全局规则)
     */
    private Long stationId;

    /**
     * 规则类型：1-基础电价，2-峰谷电价，3-阶梯电价
     */
    private Integer ruleType;

    /**
     * 时段开始时间(峰谷电价使用)
     */
    private LocalTime startTime;

    /**
     * 时段结束时间(峰谷电价使用)
     */
    private LocalTime endTime;

    /**
     * 电价(元/kWh)
     */
    private BigDecimal electricityPrice;

    /**
     * 服务费(元/kWh)
     */
    private BigDecimal servicePrice;

    /**
     * 优先级(数字越大越优先匹配)
     */
    private Integer priority;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;
}
