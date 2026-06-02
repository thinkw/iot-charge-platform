package com.iot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.core.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper 接口
 * <p>
 * 提供角色查询功能，包括根据用户ID查询角色列表。
 * </p>
 *
 * @author IoT Team
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 根据用户ID查询角色编码列表
     * <p>
     * 三表联查：user_role → role，返回该用户拥有的所有角色编码。
     * </p>
     *
     * @param userId 用户ID
     * @return 角色编码列表（如 ["ROLE_USER", "ROLE_ADMIN"]）
     */
    @Select("SELECT r.code FROM role r INNER JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> findRoleCodesByUserId(Long userId);
}
