package com.iot.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户注册请求
 *
 * @author IoT Team
 */
@Data
public class RegisterRequest {

    /** 手机号，11位数字 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 密码，6-20位 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 昵称，可选 */
    private String nickname;
}
