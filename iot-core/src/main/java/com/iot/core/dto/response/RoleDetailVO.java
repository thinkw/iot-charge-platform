package com.iot.core.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 角色详情响应 VO
 * <p>
 * 包含角色基本信息及其关联的权限ID列表，用于管理端角色编辑表单回填。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
public class RoleDetailVO {

    /** 角色ID */
    private Long id;

    /** 角色名称 */
    private String name;

    /** 角色编码 */
    private String code;

    /** 角色描述 */
    private String description;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    /** 关联的权限ID列表 */
    private List<Long> permissionIds;

    /** 创建时间 */
    private String createTime;

    /** 更新时间 */
    private String updateTime;
}
