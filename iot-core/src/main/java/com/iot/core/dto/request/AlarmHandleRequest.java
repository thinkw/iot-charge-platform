package com.iot.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 告警处理请求 DTO
 * <p>
 * 运营管理员处理告警时提交的请求参数。
 * </p>
 *
 * @author IoT Team
 */
@Data
public class AlarmHandleRequest {

    /**
     * 告警ID
     */
    @NotNull(message = "告警ID不能为空")
    private Long alarmId;

    /**
     * 处理备注（处理措施说明）
     */
    @NotBlank(message = "处理备注不能为空")
    private String handleNote;
}
