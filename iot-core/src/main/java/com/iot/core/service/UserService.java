package com.iot.core.service;

import com.iot.core.dto.request.LoginRequest;
import com.iot.core.dto.request.RegisterRequest;
import com.iot.core.dto.response.LoginResponse;
import com.iot.core.dto.response.UserInfoVO;

/**
 * 用户服务接口
 * <p>
 * 提供用户注册、登录、信息查询等核心功能。
 * 注册和登录返回用户基本信息（不含Token），
 * Token生成由Controller调用JwtUtil完成。
 * </p>
 *
 * @author IoT Team
 */
public interface UserService {

    /**
     * 用户注册
     * <p>
     * 1. 校验手机号格式和唯一性
     * 2. BCrypt 加密密码后持久化
     * 3. 返回用户基本信息
     * </p>
     *
     * @param request 注册请求（phone, password, nickname）
     * @return 登录响应（userId, phone, nickname），不含token
     * @throws com.iot.common.exception.BusinessException 手机号已注册时抛出（code=409）
     */
    LoginResponse register(RegisterRequest request);

    /**
     * 用户登录
     * <p>
     * 1. 通过手机号查询用户
     * 2. BCrypt 密码校验
     * 3. 更新 lastLogin 时间
     * 4. 返回用户基本信息
     * </p>
     *
     * @param request 登录请求（phone, password）
     * @return 登录响应（userId, phone, nickname），不含token
     * @throws com.iot.common.exception.BusinessException 手机号不存在或密码错误时抛出（code=401）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 获取当前用户信息
     *
     * @param userId 用户ID
     * @return 用户信息 VO
     * @throws com.iot.common.exception.BusinessException 用户不存在时抛出（code=404）
     */
    UserInfoVO getUserInfo(Long userId);
}
