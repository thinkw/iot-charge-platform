package com.iot.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色创建/修改请求体
 * <p>
 * 包含角色的基本信息及其关联的权限ID列表。
 * 修改角色权限时，前端传入完整的权限ID列表，后端进行全量替换。
 * </p>
 *
 * @author IoT Team
 */
@Data
public class RoleRequest {

    /** 角色名称 */
    @NotBlank(message = "角色名称不能为空")
    private String name;

    /** 角色编码（如 ROLE_ADMIN），创建后不可修改 */
    private String code;

    /** 角色描述 */
    private String description;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    /** 关联的权限ID列表（全量替换） */
    @NotNull(message = "权限列表不能为空")
    private List<Long> permissionIds;
}
