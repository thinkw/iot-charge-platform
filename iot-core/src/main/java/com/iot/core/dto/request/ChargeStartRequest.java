package com.iot.core.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 扫码启桩请求
 *
 * @author IoT Team
 */
@Data
public class ChargeStartRequest {

    /** 充电桩ID */
    @NotNull(message = "充电桩ID不能为空")
    private Long chargerId;
}
