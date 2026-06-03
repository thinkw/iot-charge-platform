package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.core.dto.response.PermissionTreeNode;
import com.iot.core.entity.Permission;
import com.iot.core.mapper.PermissionMapper;
import com.iot.core.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限管理服务实现类
 * <p>
 * 查询所有权限并构建树形结构，用于前端角色权限分配。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;

    /**
     * 获取权限树
     * <p>
     * 1. 查询所有权限（按 sort 排序）
     * 2. 按 parentId 分组
     * 3. 递归构建树形结构
     * </p>
     */
    @Override
    public List<PermissionTreeNode> getPermissionTree() {
        // 1. 查询所有权限，按 sort 升序
        List<Permission> allPermissions = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>().orderByAsc(Permission::getSort)
        );

        // 2. 按 parentId 分组
        Map<Long, List<Permission>> parentMap = allPermissions.stream()
                .collect(Collectors.groupingBy(Permission::getParentId));

        // 3. 递归构建树（顶层 parentId=0）
        return buildTree(parentMap, 0L);
    }

    /**
     * 递归构建权限树
     *
     * @param parentMap 按 parentId 分组的权限 Map
     * @param parentId  当前层级父ID
     * @return 子权限树节点列表
     */
    private List<PermissionTreeNode> buildTree(Map<Long, List<Permission>> parentMap, Long parentId) {
        List<Permission> children = parentMap.getOrDefault(parentId, new ArrayList<>());
        return children.stream().map(perm -> PermissionTreeNode.builder()
                .id(perm.getId())
                .name(perm.getName())
                .code(perm.getCode())
                .type(perm.getType())
                .parentId(perm.getParentId())
                .path(perm.getPath())
                .sort(perm.getSort())
                .children(buildTree(parentMap, perm.getId()))
                .build()
        ).collect(Collectors.toList());
    }
}
