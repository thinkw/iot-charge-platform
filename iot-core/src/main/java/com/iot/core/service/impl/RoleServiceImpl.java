package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.common.exception.BusinessException;
import com.iot.core.dto.request.RoleRequest;
import com.iot.core.dto.response.RoleDetailVO;
import com.iot.core.entity.Role;
import com.iot.core.entity.RolePermission;
import com.iot.core.mapper.RoleMapper;
import com.iot.core.mapper.RolePermissionMapper;
import com.iot.core.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现类
 * <p>
 * 实现角色的 CRUD 及权限关联管理。
 * 权限关联采用全量替换策略以确保数据一致性。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<Role> listRoles(int page, int size) {
        Page<Role> pageResult = roleMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Role>().orderByDesc(Role::getCreateTime)
        );
        return pageResult.getRecords();
    }

    @Override
    public long countRoles() {
        return roleMapper.selectCount(null);
    }

    /**
     * 获取角色详情（含关联权限ID列表）
     * <p>
     * 先查角色基本信息，再查关联的权限ID列表，组装为 VO 返回。
     * </p>
     */
    @Override
    public RoleDetailVO getRoleDetail(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }

        // 查询该角色关联的权限ID列表
        List<Long> permissionIds = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId)
        ).stream().map(RolePermission::getPermissionId).collect(Collectors.toList());

        return RoleDetailVO.builder()
                .id(role.getId())
                .name(role.getName())
                .code(role.getCode())
                .description(role.getDescription())
                .status(role.getStatus())
                .permissionIds(permissionIds)
                .createTime(role.getCreateTime() != null ? role.getCreateTime().format(FMT) : null)
                .updateTime(role.getUpdateTime() != null ? role.getUpdateTime().format(FMT) : null)
                .build();
    }

    /**
     * 创建角色及其权限关联
     * <p>
     * 1. 校验角色编码唯一性
     * 2. 插入角色记录
     * 3. 批量插入角色-权限关联
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role createRole(RoleRequest request) {
        // 校验编码唯一性
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, request.getCode())
        );
        if (count > 0) {
            throw new BusinessException(409, "角色编码已存在: " + request.getCode());
        }

        // 创建角色
        Role role = new Role();
        role.setName(request.getName());
        role.setCode(request.getCode());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        roleMapper.insert(role);

        // 保存权限关联
        saveRolePermissions(role.getId(), request.getPermissionIds());

        log.info("[角色管理] 创建角色 - roleId: {}, name: {}, code: {}, permissions: {}",
                role.getId(), role.getName(), role.getCode(), request.getPermissionIds().size());
        return role;
    }

    /**
     * 修改角色信息及权限
     * <p>
     * 编码字段通常不修改（创建后固定），权限列表全量替换。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role updateRole(Long roleId, RoleRequest request) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }

        // 更新基本信息（编码通常不变）
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus());
        roleMapper.updateById(role);

        // 全量替换权限关联：先删后插
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId)
        );
        saveRolePermissions(roleId, request.getPermissionIds());

        log.info("[角色管理] 修改角色 - roleId: {}, name: {}, permissions: {}",
                roleId, role.getName(), request.getPermissionIds().size());
        return role;
    }

    /**
     * 删除角色及其权限关联
     * <p>
     * 先删除关联的权限记录，再删除角色本身。
     * 注意：不校验是否有用户正在使用该角色，由调用方在 Controller 层做防护。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }

        // 删除权限关联
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId)
        );
        // 删除角色
        roleMapper.deleteById(roleId);

        log.info("[角色管理] 删除角色 - roleId: {}, name: {}", roleId, role.getName());
    }

    /**
     * 修改角色启用/禁用状态
     */
    @Override
    public void updateRoleStatus(Long roleId, Integer status) {
        if (status != 0 && status != 1) {
            throw new BusinessException(400, "状态值必须为0（禁用）或1（启用）");
        }

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }

        role.setStatus(status);
        roleMapper.updateById(role);
        log.info("[角色管理] 更新角色状态 - roleId: {}, status: {}", roleId, status);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 批量保存角色-权限关联
     *
     * @param roleId        角色ID
     * @param permissionIds 权限ID列表
     */
    private void saveRolePermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        List<RolePermission> list = permissionIds.stream().map(permId -> {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permId);
            return rp;
        }).collect(Collectors.toList());
        // 逐条插入（MyBatis-Plus 的 saveBatch 也可用，此处保守写法）
        for (RolePermission rp : list) {
            rolePermissionMapper.insert(rp);
        }
    }
}
