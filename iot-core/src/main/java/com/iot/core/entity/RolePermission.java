package com.iot.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色权限关联实体
 * <p>
 * 记录角色与权限的多对多关联关系。
 * </p>
 *
 * @author IoT Team
 */
@Data
@TableName("role_permission")
public class RolePermission {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 权限ID
     */
    private Long permissionId;
}
