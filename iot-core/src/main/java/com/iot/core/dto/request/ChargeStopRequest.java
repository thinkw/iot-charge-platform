package com.iot.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 停止充电请求
 *
 * @author IoT Team
 */
@Data
public class ChargeStopRequest {

    /** 订单编号 */
    @NotBlank(message = "订单编号不能为空")
    private String orderNo;
}
