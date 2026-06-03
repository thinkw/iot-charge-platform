package com.iot.core.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 权限树节点 VO
 * <p>
 * 用于管理端角色权限分配时展示的树形结构。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
public class PermissionTreeNode {

    /** 权限ID */
    private Long id;

    /** 权限名称 */
    private String name;

    /** 权限编码 */
    private String code;

    /** 类型：1-菜单，2-按钮，3-接口 */
    private Integer type;

    /** 父权限ID */
    private Long parentId;

    /** 路由路径 */
    private String path;

    /** 排序 */
    private Integer sort;

    /** 子权限列表 */
    private List<PermissionTreeNode> children;
}
