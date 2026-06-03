package com.iot.api.controller.admin;

import com.iot.api.security.SecurityUtil;
import com.iot.common.model.PageResult;
import com.iot.common.model.Result;
import com.iot.core.dto.request.RoleRequest;
import com.iot.core.dto.response.PermissionTreeNode;
import com.iot.core.dto.response.RoleDetailVO;
import com.iot.core.entity.Role;
import com.iot.core.service.PermissionService;
import com.iot.core.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 运营后台 — 角色权限管理控制器
 * <p>
 * 提供角色的 CRUD、权限树查询功能。
 * 所有接口需要 ROLE_ADMIN 权限（由 SecurityConfig 控制）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/role")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    /**
     * 分页查询角色列表
     *
     * @param page 页码，默认1
     * @param size 每页数量，默认20
     * @return 分页角色列表
     */
    @GetMapping("/list")
    public Result<PageResult<Role>> listRoles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-角色列表] 操作人: {}, page: {}", operatorId, page);

        List<Role> roles = roleService.listRoles(page, size);
        long total = roleService.countRoles();

        return Result.success(PageResult.of(roles, total, page, size));
    }

    /**
     * 获取角色详情（含关联权限ID列表）
     *
     * @param id 角色ID
     * @return 角色详情
     */
    @GetMapping("/{id}")
    public Result<RoleDetailVO> getRoleDetail(@PathVariable Long id) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-角色详情] 操作人: {}, roleId: {}", operatorId, id);

        RoleDetailVO detail = roleService.getRoleDetail(id);
        return Result.success(detail);
    }

    /**
     * 新增角色及其权限
     *
     * @param request 角色请求体（含权限ID列表）
     * @return 新增的角色
     */
    @PostMapping
    public Result<Role> createRole(@RequestBody @Valid RoleRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-新增角色] 操作人: {}, name: {}, code: {}, permissions: {}",
                operatorId, request.getName(), request.getCode(), request.getPermissionIds().size());

        Role role = roleService.createRole(request);
        return Result.success(role);
    }

    /**
     * 修改角色及其权限
     * <p>
     * 权限全量替换：先删除旧关联，再插入新关联。
     * </p>
     *
     * @param id      角色ID
     * @param request 角色请求体
     * @return 修改后的角色
     */
    @PutMapping("/{id}")
    public Result<Role> updateRole(@PathVariable Long id,
                                   @RequestBody @Valid RoleRequest request) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-修改角色] 操作人: {}, roleId: {}, name: {}, permissions: {}",
                operatorId, id, request.getName(), request.getPermissionIds().size());

        Role role = roleService.updateRole(id, request);
        return Result.success(role);
    }

    /**
     * 删除角色
     *
     * @param id 角色ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteRole(@PathVariable Long id) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-删除角色] 操作人: {}, roleId: {}", operatorId, id);

        roleService.deleteRole(id);
        return Result.success("删除成功");
    }

    /**
     * 修改角色启用/禁用状态
     *
     * @param id     角色ID
     * @param status 状态：0-禁用，1-启用
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    public Result<String> updateRoleStatus(@PathVariable Long id,
                                           @RequestParam Integer status) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-角色状态] 操作人: {}, roleId: {}, status: {}", operatorId, id, status);

        if (status != 0 && status != 1) {
            return Result.error(400, "状态值必须为0（禁用）或1（启用）");
        }
        roleService.updateRoleStatus(id, status);
        return Result.success(status == 1 ? "已启用" : "已禁用");
    }

    /**
     * 获取权限树
     * <p>
     * 返回所有权限的树形结构，用于角色权限分配时的勾选。
     * </p>
     *
     * @return 权限树
     */
    @GetMapping("/permission/tree")
    public Result<List<PermissionTreeNode>> getPermissionTree() {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-权限树] 操作人: {}", operatorId);

        List<PermissionTreeNode> tree = permissionService.getPermissionTree();
        return Result.success(tree);
    }
}
