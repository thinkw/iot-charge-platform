package com.iot.core.service;

import com.iot.core.dto.response.PermissionTreeNode;

import java.util.List;

/**
 * 权限管理服务接口
 * <p>
 * 提供权限树的查询功能，用于角色权限分配时的树形选择器。
 * </p>
 *
 * @author IoT Team
 */
public interface PermissionService {

    /**
     * 获取权限树
     * <p>
     * 查询所有权限并按 parentId 组装为树形结构。
     * 顶层节点 parentId=0，子节点嵌套在 children 中。
     * </p>
     *
     * @return 权限树节点列表（顶层）
     */
    List<PermissionTreeNode> getPermissionTree();
}
