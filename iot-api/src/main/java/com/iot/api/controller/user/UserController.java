package com.iot.api.controller.user;

import com.iot.api.security.JwtUtil;
import com.iot.api.security.SecurityUtil;
import com.iot.common.model.Result;
import com.iot.core.dto.request.LoginRequest;
import com.iot.core.dto.request.RegisterRequest;
import com.iot.core.dto.response.LoginResponse;
import com.iot.core.dto.response.UserInfoVO;
import com.iot.core.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端控制器
 * <p>
 * 提供用户注册、登录、个人信息查询接口。
 * 注册和登录接口无需认证（SecurityConfig 中配置 permitAll），
 * 其余接口需要携带 JWT Token。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册
     * <p>
     * 注册成功后自动生成 JWT Token 并返回，用户无需再次登录。
     * </p>
     *
     * @param request 注册请求（phone, password, nickname）
     * @return 含 JWT Token 的登录响应
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest request) {
        log.info("[用户注册] phone: {}", request.getPhone());

        // 调用注册服务
        LoginResponse response = userService.register(request);

        // 注册成功，生成 JWT Token（包含角色信息）
        String token = jwtUtil.generateToken(response.getUserId(), response.getPhone(), response.getRoles());
        response.setToken(token);

        return Result.success(response);
    }

    /**
     * 用户登录
     * <p>
     * 验证手机号和密码，成功后返回 JWT Token。
     * </p>
     *
     * @param request 登录请求（phone, password）
     * @return 含 JWT Token 的登录响应
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        log.info("[用户登录] phone: {}", request.getPhone());

        // 调用登录服务
        LoginResponse response = userService.login(request);

        // 登录成功，生成 JWT Token（包含角色信息）
        String token = jwtUtil.generateToken(response.getUserId(), response.getPhone(), response.getRoles());
        response.setToken(token);

        return Result.success(response);
    }

    /**
     * 获取当前登录用户信息
     * <p>
     * 需要携带有效的 JWT Token，从 SecurityContext 中获取当前用户ID。
     * </p>
     *
     * @return 用户信息
     */
    @GetMapping("/info")
    public Result<UserInfoVO> getUserInfo() {
        Long userId = SecurityUtil.getCurrentUserId();
        log.info("[获取用户信息] userId: {}", userId);

        UserInfoVO userInfo = userService.getUserInfo(userId);
        return Result.success(userInfo);
    }
}
