package com.iot.core.service;

import com.iot.core.dto.request.RoleRequest;
import com.iot.core.dto.response.RoleDetailVO;
import com.iot.core.entity.Role;

import java.util.List;

/**
 * 角色管理服务接口
 * <p>
 * 提供角色的 CRUD 操作及权限分配功能。
 * 角色关联权限采用全量替换策略：修改时前端传入完整权限ID列表，后端删除旧关联后重新插入。
 * </p>
 *
 * @author IoT Team
 */
public interface RoleService {

    /**
     * 分页查询角色列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 角色列表
     */
    List<Role> listRoles(int page, int size);

    /**
     * 查询角色总数
     *
     * @return 角色总数
     */
    long countRoles();

    /**
     * 获取角色详情（含关联权限ID列表）
     *
     * @param roleId 角色ID
     * @return 角色详情 VO
     */
    RoleDetailVO getRoleDetail(Long roleId);

    /**
     * 创建角色及其权限关联
     *
     * @param request 角色请求体
     * @return 创建的角色
     */
    Role createRole(RoleRequest request);

    /**
     * 修改角色信息及权限
     * <p>
     * 权限采用全量替换策略：先删除旧关联，再插入新关联。
     * </p>
     *
     * @param roleId  角色ID
     * @param request 角色请求体
     * @return 修改后的角色
     */
    Role updateRole(Long roleId, RoleRequest request);

    /**
     * 删除角色及其关联
     *
     * @param roleId 角色ID
     */
    void deleteRole(Long roleId);

    /**
     * 修改角色启用/禁用状态
     *
     * @param roleId 角色ID
     * @param status 状态：0-禁用，1-启用
     */
    void updateRoleStatus(Long roleId, Integer status);
}
