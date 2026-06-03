package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.common.exception.BusinessException;
import com.iot.core.dto.request.LoginRequest;
import com.iot.core.dto.request.RegisterRequest;
import com.iot.core.dto.response.LoginResponse;
import com.iot.core.dto.response.UserInfoVO;
import com.iot.core.entity.User;
import com.iot.core.mapper.RoleMapper;
import com.iot.core.mapper.UserMapper;
import com.iot.core.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务实现类
 * <p>
 * 提供用户注册、登录和信息查询的具体实现。
 * 密码使用 BCrypt 加密存储，登录时进行密文匹配。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户注册
     * <p>
     * 校验手机号唯一性，BCrypt 加密密码后写入数据库。
     * 注册成功直接返回用户信息（后续 Controller 层生成 JWT Token）。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        // 1. 校验手机号唯一性
        long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, request.getPhone())
        );
        if (count > 0) {
            throw new BusinessException(409, "该手机号已注册，请直接登录");
        }

        // 2. 创建用户实体
        User user = new User();
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getPhone());
        user.setStatus(1); // 正常状态

        userMapper.insert(user);

        log.info("[用户注册] userId: {}, phone: {}", user.getId(), user.getPhone());

        // 3. 查询角色（新注册用户默认 ROLE_USER）
        List<String> roles = roleMapper.findRoleCodesByUserId(user.getId());
        if (roles.isEmpty()) {
            roles = List.of("ROLE_USER");
        }

        // 4. 返回注册结果
        return LoginResponse.builder()
                .userId(user.getId())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .roles(roles)
                .build();
    }

    /**
     * 用户登录
     * <p>
     * 通过手机号查询用户，BCrypt 验证密码，更新最后登录时间。
     * </p>
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, request.getPhone())
        );
        if (user == null) {
            throw new BusinessException(401, "手机号或密码错误");
        }

        // 2. 校验账户状态
        if (user.getStatus() == 0) {
            throw new BusinessException(403, "账户已被禁用，请联系管理员");
        }

        // 3. 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "手机号或密码错误");
        }

        // 4. 更新最后登录时间
        user.setLastLogin(LocalDateTime.now());
        userMapper.updateById(user);

        // 5. 查询角色
        List<String> roles = roleMapper.findRoleCodesByUserId(user.getId());
        if (roles.isEmpty()) {
            roles = List.of("ROLE_USER");
        }

        log.info("[用户登录] userId: {}, phone: {}, roles: {}", user.getId(), user.getPhone(), roles);

        // 6. 返回登录结果
        return LoginResponse.builder()
                .userId(user.getId())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .roles(roles)
                .build();
    }

    /**
     * 获取用户信息
     */
    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        return UserInfoVO.builder()
                .userId(user.getId())
                .phone(user.getPhone())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .plateNo(user.getPlateNo())
                .carModel(user.getCarModel())
                .build();
    }

    // ==================== 管理端方法 ====================

    /**
     * 管理端 — 分页查询用户列表
     * <p>
     * 支持按手机号模糊搜索和状态筛选。
     * 不返回密码字段。
     * </p>
     */
    @Override
    public List<User> adminListUsers(String phone, Integer status, int page, int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(phone != null && !phone.isBlank(), User::getPhone, phone);
        wrapper.eq(status != null, User::getStatus, status);
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> pageResult = userMapper.selectPage(new Page<>(page, size), wrapper);
        // 脱敏处理：清除密码字段
        pageResult.getRecords().forEach(user -> user.setPassword(null));
        return pageResult.getRecords();
    }

    /**
     * 管理端 — 查询用户总数
     */
    @Override
    public long adminCountUsers(String phone, Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(phone != null && !phone.isBlank(), User::getPhone, phone);
        wrapper.eq(status != null, User::getStatus, status);
        return userMapper.selectCount(wrapper);
    }

    /**
     * 管理端 — 启用/禁用用户
     * <p>
     * 修改用户状态。禁用后该用户无法登录，登录接口会抛出 403。
     * </p>
     */
    @Override
    public void adminUpdateUserStatus(Long userId, Integer status) {
        if (status != 0 && status != 1) {
            throw new BusinessException(400, "状态值必须为0（禁用）或1（启用）");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        user.setStatus(status);
        userMapper.updateById(user);
        log.info("[用户管理] 更新用户状态 - userId: {}, status: {}", userId, status);
    }
}
