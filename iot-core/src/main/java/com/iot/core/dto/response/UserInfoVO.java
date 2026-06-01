package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息 VO
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {

    /** 用户ID */
    private Long userId;

    /** 手机号 */
    private String phone;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;

    /** 车牌号 */
    private String plateNo;

    /** 车型 */
    private String carModel;
}
