package com.iot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.core.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色关联 Mapper
 *
 * @author IoT Team
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
