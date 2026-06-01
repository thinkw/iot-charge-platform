package com.iot.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 支付/退款请求
 * <p>
 * charge/pay 和 order/refund 接口共用此请求体。
 * </p>
 *
 * @author IoT Team
 */
@Data
public class PayOrderRequest {

    /** 订单编号 */
    @NotBlank(message = "订单编号不能为空")
    private String orderNo;
}
