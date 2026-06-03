package com.iot.core.service;

import com.iot.core.dto.response.PermissionTreeNode;
import com.iot.core.entity.Permission;
import com.iot.core.mapper.PermissionMapper;
import com.iot.core.service.impl.PermissionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * PermissionService 单元测试
 * <p>
 * 覆盖权限树构建逻辑。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PermissionService 单元测试")
class PermissionServiceTest {

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @Test
    @DisplayName("获取权限树 - 包含父子结构")
    void getPermissionTree_ReturnsTree() {
        // 构建测试数据: 用户端(parentId=1), 下设充电站查询(parentId=2)
        Permission p1 = buildPerm(1L, "用户端", "user", 0L, 1);
        Permission p2 = buildPerm(2L, "充电站查询", "user:station", 1L, 1);
        Permission p3 = buildPerm(3L, "充电管理", "user:charge", 1L, 2);
        when(permissionMapper.selectList(any())).thenReturn(List.of(p1, p2, p3));

        List<PermissionTreeNode> tree = permissionService.getPermissionTree();
        assertNotNull(tree);
        assertEquals(1, tree.size()); // 顶层1个节点
        assertEquals("用户端", tree.get(0).getName());
        assertEquals(2, tree.get(0).getChildren().size()); // 2个子节点
        assertEquals("充电站查询", tree.get(0).getChildren().get(0).getName());
    }

    @Test
    @DisplayName("获取权限树 - 空数据返回空列表")
    void getPermissionTree_Empty() {
        when(permissionMapper.selectList(any())).thenReturn(List.of());
        List<PermissionTreeNode> tree = permissionService.getPermissionTree();
        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    private Permission buildPerm(Long id, String name, String code, Long parentId, int sort) {
        Permission p = new Permission();
        p.setId(id); p.setName(name); p.setCode(code);
        p.setParentId(parentId); p.setType(1); p.setSort(sort);
        return p;
    }
}
