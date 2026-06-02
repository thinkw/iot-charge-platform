package com.iot.api.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.core.entity.User;
import com.iot.core.mapper.RoleMapper;
import com.iot.core.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetailsService 实现
 * <p>
 * 根据手机号（作为用户名）加载用户信息，用于 JWT 认证流程。
 * 从数据库加载用户实际角色（user_role + role 表），支持 ROLE_USER / ROLE_ADMIN / ROLE_SYSTEM。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    /**
     * 根据手机号加载用户详情
     * <p>
     * 从数据库查询用户信息，构建 Spring Security UserDetails 对象。
     * 当前阶段暂不加载角色权限（阶段3完善），全部赋予 ROLE_USER 角色。
     * </p>
     *
     * @param username 用户名（本项目使用手机号）
     * @return UserDetails 用户详情
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, username)
        );

        if (user == null) {
            log.warn("[认证] 用户不存在 - phone: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("[认证] 用户已被禁用 - phone: {}", username);
            throw new UsernameNotFoundException("用户已被禁用: " + username);
        }

        // 从数据库加载用户的实际角色
        List<String> roleCodes = roleMapper.findRoleCodesByUserId(user.getId());
        if (roleCodes.isEmpty()) {
            log.warn("[认证] 用户无任何角色 - userId: {}, phone: {}", user.getId(), username);
            roleCodes = List.of("ROLE_USER"); // 默认给予普通用户角色
        }
        List<SimpleGrantedAuthority> authorities = roleCodes.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        log.debug("[认证] 用户角色加载 - userId: {}, roles: {}", user.getId(), roleCodes);

        return new org.springframework.security.core.userdetails.User(
                user.getPhone(),
                user.getPassword(),
                authorities
        );
    }
}
