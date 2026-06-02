package com.iot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.core.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限 Mapper
 *
 * @author IoT Team
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
