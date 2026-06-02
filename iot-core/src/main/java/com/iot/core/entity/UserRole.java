package com.iot.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户角色关联实体
 * <p>
 * 记录用户与角色的多对多关联关系。
 * </p>
 *
 * @author IoT Team
 */
@Data
@TableName("user_role")
public class UserRole {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色ID
     */
    private Long roleId;
}
