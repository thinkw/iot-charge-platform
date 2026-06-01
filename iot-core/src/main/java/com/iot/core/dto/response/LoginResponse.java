package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应
 * <p>
 * 登录成功时返回 JWT Token 和用户基本信息。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT Token */
    private String token;

    /** 用户ID */
    private Long userId;

    /** 手机号 */
    private String phone;

    /** 昵称 */
    private String nickname;
}
