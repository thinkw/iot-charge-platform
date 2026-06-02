package com.iot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.core.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色权限关联 Mapper
 *
 * @author IoT Team
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
