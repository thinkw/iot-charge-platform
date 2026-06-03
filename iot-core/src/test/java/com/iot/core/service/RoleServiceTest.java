package com.iot.core.service;

import com.iot.common.exception.BusinessException;
import com.iot.core.dto.request.RoleRequest;
import com.iot.core.dto.response.RoleDetailVO;
import com.iot.core.entity.Role;
import com.iot.core.entity.RolePermission;
import com.iot.core.mapper.RoleMapper;
import com.iot.core.mapper.RolePermissionMapper;
import com.iot.core.service.impl.RoleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RoleService 单元测试
 * <p>
 * 覆盖角色的基本 CRUD 操作和权限管理逻辑。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RoleService 单元测试")
class RoleServiceTest {

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    // ==================== 查询测试 ====================

    @Test
    @DisplayName("查询角色列表 - 返回正常分页数据")
    void listRoles_ReturnsPageResult() {
        Role role = new Role();
        role.setId(1L); role.setName("管理员"); role.setCode("ROLE_ADMIN");
        when(roleMapper.selectPage(any(), any())).thenReturn(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20) {{
                    setRecords(List.of(role));
                }}
        );

        List<Role> result = roleService.listRoles(1, 20);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ROLE_ADMIN", result.get(0).getCode());
    }

    @Test
    @DisplayName("查询角色详情 - 返回角色和权限ID列表")
    void getRoleDetail_ReturnsRoleWithPermissions() {
        Role role = new Role();
        role.setId(1L); role.setName("管理员"); role.setCode("ROLE_ADMIN");
        role.setDescription("系统管理员"); role.setStatus(1);
        role.setCreateTime(LocalDateTime.now()); role.setUpdateTime(LocalDateTime.now());
        when(roleMapper.selectById(1L)).thenReturn(role);
        // Mock 权限关联列表
        com.iot.core.entity.RolePermission rp = new com.iot.core.entity.RolePermission();
        rp.setPermissionId(1L);
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rp));

        RoleDetailVO detail = roleService.getRoleDetail(1L);
        assertNotNull(detail);
        assertEquals("ROLE_ADMIN", detail.getCode());
        assertEquals(1, detail.getPermissionIds().size());
        assertEquals(1L, detail.getPermissionIds().get(0));
    }

    @Test
    @DisplayName("查询角色详情 - 角色不存在抛出404")
    void getRoleDetail_NotFound_Throws404() {
        when(roleMapper.selectById(999L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> roleService.getRoleDetail(999L));
        assertEquals(404, ex.getCode());
    }

    // ==================== 创建测试 ====================

    @Test
    @DisplayName("创建角色 - 编码已存在时抛出409")
    void createRole_DuplicateCode_Throws409() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        RoleRequest request = new RoleRequest();
        request.setName("测试角色"); request.setCode("ROLE_ADMIN");
        request.setPermissionIds(List.of(1L));

        BusinessException ex = assertThrows(BusinessException.class, () -> roleService.createRole(request));
        assertEquals(409, ex.getCode());
        verify(roleMapper, never()).insert(any(Role.class));
    }

    @Test
    @DisplayName("创建角色 - 编码唯一时创建成功")
    void createRole_Success() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.insert(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(10L);
            return 1;
        });
        when(rolePermissionMapper.insert(any(RolePermission.class))).thenReturn(1);

        RoleRequest request = new RoleRequest();
        request.setName("编辑"); request.setCode("ROLE_EDITOR");
        request.setDescription("内容编辑"); request.setPermissionIds(List.of(5L, 6L));

        Role created = roleService.createRole(request);
        assertNotNull(created);
        assertEquals(10L, created.getId());
        assertEquals("ROLE_EDITOR", created.getCode());
        verify(rolePermissionMapper, times(2)).insert(any(RolePermission.class));
    }

    // ==================== 修改测试 ====================

    @Test
    @DisplayName("修改角色 - 权限全量替换")
    void updateRole_ReplacesPermissions() {
        Role existing = new Role();
        existing.setId(1L); existing.setName("管理员"); existing.setCode("ROLE_ADMIN");
        when(roleMapper.selectById(1L)).thenReturn(existing);
        when(roleMapper.updateById(any(Role.class))).thenReturn(1);
        when(rolePermissionMapper.delete(any())).thenReturn(1);
        when(rolePermissionMapper.insert(any(RolePermission.class))).thenReturn(1);

        RoleRequest request = new RoleRequest();
        request.setName("超级管理员"); request.setCode("ROLE_ADMIN");
        request.setPermissionIds(List.of(1L, 2L, 3L));

        Role updated = roleService.updateRole(1L, request);
        assertEquals("超级管理员", updated.getName());
        verify(rolePermissionMapper, times(1)).delete(any());
        verify(rolePermissionMapper, times(3)).insert(any(RolePermission.class));
    }

    @Test
    @DisplayName("修改角色 - 角色不存在抛出404")
    void updateRole_NotFound_Throws404() {
        when(roleMapper.selectById(999L)).thenReturn(null);
        RoleRequest request = new RoleRequest();
        request.setName("X"); request.setCode("X"); request.setPermissionIds(List.of());

        assertThrows(BusinessException.class, () -> roleService.updateRole(999L, request));
    }

    // ==================== 删除测试 ====================

    @Test
    @DisplayName("删除角色 - 删除角色和权限关联")
    void deleteRole_RemovesRoleAndPermissions() {
        Role role = new Role();
        role.setId(1L); role.setName("测试"); role.setCode("ROLE_TEST");
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(rolePermissionMapper.delete(any())).thenReturn(1);
        when(roleMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> roleService.deleteRole(1L));
        verify(rolePermissionMapper).delete(any());
        verify(roleMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除角色 - 角色不存在抛出404")
    void deleteRole_NotFound_Throws404() {
        when(roleMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> roleService.deleteRole(999L));
    }

    // ==================== 状态测试 ====================

    @Test
    @DisplayName("修改角色状态 - 正常切换")
    void updateRoleStatus_Success() {
        Role role = new Role();
        role.setId(1L); role.setStatus(1);
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleMapper.updateById(any(Role.class))).thenReturn(1);

        assertDoesNotThrow(() -> roleService.updateRoleStatus(1L, 0));
        assertEquals(0, role.getStatus());
    }

    @Test
    @DisplayName("修改角色状态 - 非法状态值抛出400")
    void updateRoleStatus_InvalidStatus_Throws400() {
        assertThrows(BusinessException.class, () -> roleService.updateRoleStatus(1L, 9));
    }
}
