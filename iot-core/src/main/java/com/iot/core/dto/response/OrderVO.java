package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充电订单 VO
 * <p>
 * 用于订单列表和详情展示，关联了充电桩名称和站点名称。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {

    /** 订单ID */
    private Long id;

    /** 订单编号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 充电桩ID */
    private Long chargerId;

    /** 充电桩名称 */
    private String chargerName;

    /** 充电站ID */
    private Long stationId;

    /** 充电站名称 */
    private String stationName;

    /** 开始充电时间 */
    private LocalDateTime startTime;

    /** 结束充电时间 */
    private LocalDateTime endTime;

    /** 充电总量(kWh) */
    private BigDecimal chargedEnergy;

    /** 总金额(元) */
    private BigDecimal totalAmount;

    /** 电费(元) */
    private BigDecimal electricityFee;

    /** 服务费(元) */
    private BigDecimal serviceFee;

    /** 支付状态：0-未支付，1-已支付，2-已退款 */
    private Integer payStatus;

    /** 支付状态描述 */
    private String payStatusDesc;

    /** 订单状态：0-待支付，1-充电中，2-已完成，3-已取消，4-异常 */
    private Integer orderStatus;

    /** 订单状态描述 */
    private String orderStatusDesc;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
