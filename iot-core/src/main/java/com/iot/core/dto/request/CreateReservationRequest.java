package com.iot.core.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 创建预约请求
 *
 * @author IoT Team
 */
@Data
public class CreateReservationRequest {

    /** 充电桩ID */
    @NotNull(message = "充电桩ID不能为空")
    private Long chargerId;

    /** 预约日期 */
    @NotNull(message = "预约日期不能为空")
    @Future(message = "预约日期必须在未来")
    private LocalDate reserveDate;

    /** 预约开始时间 */
    @NotNull(message = "预约开始时间不能为空")
    private LocalTime startTime;

    /** 预约结束时间 */
    @NotNull(message = "预约结束时间不能为空")
    private LocalTime endTime;
}
