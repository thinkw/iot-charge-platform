package com.iot.api.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.core.entity.User;
import com.iot.core.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Spring Security UserDetailsService 实现
 * <p>
 * 根据手机号（作为用户名）加载用户信息，用于 JWT 认证流程。
 * 当前为阶段2的最小实现，阶段3将扩展角色/权限加载。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

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

        // 阶段3将根据 user_role 关联表加载实际角色
        return new org.springframework.security.core.userdetails.User(
                user.getPhone(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
