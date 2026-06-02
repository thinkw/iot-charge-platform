package com.iot.core.service;

import com.iot.common.exception.BusinessException;
import com.iot.core.dto.request.LoginRequest;
import com.iot.core.dto.request.RegisterRequest;
import com.iot.core.dto.response.LoginResponse;
import com.iot.core.dto.response.UserInfoVO;
import com.iot.core.entity.User;
import com.iot.core.mapper.RoleMapper;
import com.iot.core.mapper.UserMapper;
import com.iot.core.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 * <p>
 * 使用 Mockito 隔离 Mapper 和 PasswordEncoder 层依赖，
 * 重点测试注册、登录和用户信息查询中的业务逻辑：
 * - 手机号唯一性校验
 * - 密码加密与验证
 * - 账户状态检查
 * - 最后登录时间更新
 * - 昵称默认值逻辑
 * </p>
 *
 * @author IoT Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserService 单元测试")
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private static final String TEST_PHONE = "13800138000";
    private static final String TEST_PASSWORD = "123456";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedTestHash";

    // ==================== 注册测试 ====================

    /**
     * 注册 - 手机号已存在时抛出 BusinessException(409)，提示"该手机号已注册，请直接登录"
     * <p>
     * 验证：selectCount 返回 >0 时报错，insert 不会被调用。
     * </p>
     */
    @Test
    @DisplayName("注册 - 手机号已注册，抛出409异常")
    void register_PhoneAlreadyExists() {
        // mock: 手机号已存在
        when(userMapper.selectCount(any())).thenReturn(1L);

        RegisterRequest request = new RegisterRequest();
        request.setPhone(TEST_PHONE);
        request.setPassword(TEST_PASSWORD);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(request));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("已注册"));

        // 验证：insert 不应被调用（使用 doNothing+never 方式避免 MyBatis-Plus 重载歧义）
        verify(userMapper, never()).insert(ArgumentMatchers.<User>any());
    }

    /**
     * 注册 - 正常注册，昵称默认为手机号
     * <p>
     * 验证：未传入 nickname 时，注册成功后 nickname 与 phone 相同。
     * insert 后自动生成的主键 ID 应正确回填到返回结果中。
     * </p>
     */
    @Test
    @DisplayName("注册 - 正常注册，昵称默认为手机号")
    void register_WithDefaultNickname() {
        // mock: 手机号唯一性校验通过
        when(userMapper.selectCount(any())).thenReturn(0L);
        // mock: 密码加密
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        // mock: insert 时模拟 MyBatis-Plus 自动回填主键 ID
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));
        // mock: 角色查询返回默认普通用户角色
        when(roleMapper.findRoleCodesByUserId(anyLong())).thenReturn(List.of("ROLE_USER"));

        RegisterRequest request = new RegisterRequest();
        request.setPhone(TEST_PHONE);
        request.setPassword(TEST_PASSWORD);
        // nickname 不设置，期望默认为手机号

        LoginResponse response = userService.register(request);

        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals(TEST_PHONE, response.getPhone());
        // 未传 nickname 时应默认为手机号
        assertEquals(TEST_PHONE, response.getNickname());
    }

    /**
     * 注册 - 自定义昵称注册
     * <p>
     * 验证：传入 nickname 时，使用自定义昵称而不是手机号。
     * </p>
     */
    @Test
    @DisplayName("注册 - 自定义昵称注册")
    void register_WithCustomNickname() {
        // mock: 手机号唯一性校验通过
        when(userMapper.selectCount(any())).thenReturn(0L);
        // mock: 密码加密
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        // mock: insert 时模拟 MyBatis-Plus 自动回填主键 ID
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return 1;
        }).when(userMapper).insert(any(User.class));
        // mock: 角色查询返回默认普通用户角色
        when(roleMapper.findRoleCodesByUserId(anyLong())).thenReturn(List.of("ROLE_USER"));

        RegisterRequest request = new RegisterRequest();
        request.setPhone(TEST_PHONE);
        request.setPassword(TEST_PASSWORD);
        request.setNickname("自定义昵称");

        LoginResponse response = userService.register(request);

        assertNotNull(response);
        assertEquals(2L, response.getUserId());
        assertEquals(TEST_PHONE, response.getPhone());
        // 传入自定义昵称，应使用自定义值
        assertEquals("自定义昵称", response.getNickname());
    }

    // ==================== 登录测试 ====================

    /**
     * 登录 - 手机号不存在时抛出 BusinessException(401)
     * <p>
     * 验证：selectOne 返回 null 时报错，提示"手机号或密码错误"。
     * </p>
     */
    @Test
    @DisplayName("登录 - 手机号不存在，抛出401异常")
    void login_PhoneNotFound() {
        // mock: 根据手机号查不到用户
        when(userMapper.selectOne(any())).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setPhone(TEST_PHONE);
        request.setPassword(TEST_PASSWORD);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(request));
        assertEquals(401, ex.getCode());
    }

    /**
     * 登录 - 密码错误时抛出 BusinessException(401)
     * <p>
     * 验证：用户存在但密码不匹配时，passwordEncoder.matches 返回 false，报 401。
     * </p>
     */
    @Test
    @DisplayName("登录 - 密码错误，抛出401异常")
    void login_WrongPassword() {
        // 准备：正常用户
        User user = new User();
        user.setId(1L);
        user.setPhone(TEST_PHONE);
        user.setPassword(ENCODED_PASSWORD);
        user.setStatus(1); // 正常状态

        // mock: 用户存在
        when(userMapper.selectOne(any())).thenReturn(user);
        // mock: 密码不匹配
        when(passwordEncoder.matches(TEST_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setPhone(TEST_PHONE);
        request.setPassword(TEST_PASSWORD);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(request));
        assertEquals(401, ex.getCode());
    }

    /**
     * 登录 - 账户被禁用(status=0)时抛出 BusinessException(403)
     * <p>
     * 验证：用户状态为 0 时，应直接抛出 403 异常，不应校验密码。
     * </p>
     */
    @Test
    @DisplayName("登录 - 账户被禁用，抛出403异常")
    void login_AccountDisabled() {
        // 准备：被禁用的用户
        User user = new User();
        user.setId(1L);
        user.setPhone(TEST_PHONE);
        user.setPassword(ENCODED_PASSWORD);
        user.setStatus(0); // 禁用状态

        // mock: 用户存在
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setPhone(TEST_PHONE);
        request.setPassword(TEST_PASSWORD);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(request));
        assertEquals(403, ex.getCode());

        // 验证：账户禁用时不应校验密码
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    /**
     * 登录 - 登录成功，返回 LoginResponse，并更新 lastLogin
     * <p>
     * 验证：登录成功后返回正确的用户信息，且 updateById 被调用并设置了最后登录时间。
     * </p>
     */
    @Test
    @DisplayName("登录 - 登录成功，更新 lastLogin")
    void login_Success() {
        // 准备：正常用户
        User user = new User();
        user.setId(1L);
        user.setPhone(TEST_PHONE);
        user.setPassword(ENCODED_PASSWORD);
        user.setNickname("测试用户");
        user.setStatus(1); // 正常状态

        // mock: 用户存在，密码匹配
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches(TEST_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        // mock: 角色查询返回管理员角色
        when(roleMapper.findRoleCodesByUserId(anyLong())).thenReturn(
                List.of("ROLE_USER", "ROLE_ADMIN"));

        LoginRequest request = new LoginRequest();
        request.setPhone(TEST_PHONE);
        request.setPassword(TEST_PASSWORD);

        LoginResponse response = userService.login(request);

        // 验证返回信息正确
        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals(TEST_PHONE, response.getPhone());
        assertEquals("测试用户", response.getNickname());

        // 验证 lastLogin 被更新
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertNotNull(userCaptor.getValue().getLastLogin());
    }

    // ==================== 获取用户信息测试 ====================

    /**
     * getUserInfo - 用户不存在时抛出 BusinessException(404)
     * <p>
     * 验证：selectById 返回 null 时报 404 错误。
     * </p>
     */
    @Test
    @DisplayName("获取用户信息 - 用户不存在，抛出404异常")
    void getUserInfo_UserNotFound() {
        // mock: 用户不存在
        when(userMapper.selectById(anyLong())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.getUserInfo(999L));
        assertEquals(404, ex.getCode());
    }

    /**
     * getUserInfo - 正常返回 UserInfoVO，含车牌号等信息
     * <p>
     * 验证：返回完整用户信息，包括车牌号(plateNo)、车型(carModel)等。
     * </p>
     */
    @Test
    @DisplayName("获取用户信息 - 正常返回，含完整信息")
    void getUserInfo_Success() {
        // 准备：完整用户信息
        User user = new User();
        user.setId(1L);
        user.setPhone(TEST_PHONE);
        user.setNickname("测试用户");
        user.setAvatar("http://example.com/avatar.jpg");
        user.setPlateNo("京A12345");
        user.setCarModel("特斯拉 Model 3");

        // mock: 用户存在
        when(userMapper.selectById(1L)).thenReturn(user);

        UserInfoVO vo = userService.getUserInfo(1L);

        // 验证完整信息
        assertNotNull(vo);
        assertEquals(1L, vo.getUserId());
        assertEquals(TEST_PHONE, vo.getPhone());
        assertEquals("测试用户", vo.getNickname());
        assertEquals("http://example.com/avatar.jpg", vo.getAvatar());
        assertEquals("京A12345", vo.getPlateNo());
        assertEquals("特斯拉 Model 3", vo.getCarModel());
    }
}
