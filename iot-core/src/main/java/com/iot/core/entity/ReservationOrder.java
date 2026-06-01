package com.iot.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.iot.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 预约订单实体
 *
 * @author IoT Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reservation_order")
public class ReservationOrder extends BaseEntity {

    /**
     * 预约编号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 充电桩ID
     */
    private Long chargerId;

    /**
     * 充电站ID
     */
    private Long stationId;

    /**
     * 预约日期
     */
    private LocalDate reserveDate;

    /**
     * 预约开始时间
     */
    private LocalTime startTime;

    /**
     * 预约结束时间
     */
    private LocalTime endTime;

    /**
     * 预约押金(元)
     */
    private BigDecimal deposit;

    /**
     * 违约金(元)
     */
    private BigDecimal penalty;

    /**
     * 状态：0-待使用，1-已使用，2-已取消，3-已超时
     */
    private Integer status;

    /**
     * 支付状态：0-未支付，1-已支付，2-已退款
     */
    private Integer payStatus;

    /**
     * 是否已发送提醒：0-否，1-是
     */
    private Integer reminderSent;
}
