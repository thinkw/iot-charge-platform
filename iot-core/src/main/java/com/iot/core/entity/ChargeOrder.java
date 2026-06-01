package com.iot.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.iot.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充电订单实体
 *
 * @author IoT Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("charge_order")
public class ChargeOrder extends BaseEntity {

    /**
     * 订单编号
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
     * 开始充电时间
     */
    private LocalDateTime startTime;

    /**
     * 结束充电时间
     */
    private LocalDateTime endTime;

    /**
     * 充电总量(kWh)
     */
    private BigDecimal chargedEnergy;

    /**
     * 总金额(元)
     */
    private BigDecimal totalAmount;

    /**
     * 电费(元)
     */
    private BigDecimal electricityFee;

    /**
     * 服务费(元)
     */
    private BigDecimal serviceFee;

    /**
     * 支付状态：0-未支付，1-已支付，2-已退款
     */
    private Integer payStatus;

    /**
     * 订单状态：0-待支付，1-充电中，2-已完成，3-已取消，4-异常
     */
    private Integer orderStatus;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 取消原因
     */
    private String cancelReason;

    /**
     * 乐观锁版本号，用于并发控制
     */
    @Version
    private Integer version;
}
