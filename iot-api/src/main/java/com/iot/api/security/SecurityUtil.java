package com.iot.api.security;

import com.iot.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security 上下文工具类
 * <p>
 * 提供从 SecurityContext 中获取当前登录用户信息的便捷方法。
 * JwtAuthFilter 在认证成功后将 userId 存入 authentication.details，
 * 本工具类通过该字段获取当前用户ID。
 * </p>
 *
 * @author IoT Team
 */
public class SecurityUtil {

    private SecurityUtil() {
        // 工具类禁止实例化
    }

    /**
     * 获取当前登录用户的 ID
     * <p>
     * 从 SecurityContextHolder 中获取当前认证信息，
     * 读取 authentication.details 字段（由 JwtAuthFilter 设置）。
     * </p>
     *
     * @return 当前用户ID
     * @throws BusinessException 未登录或无法获取用户信息时抛出（code=401）
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(401, "未登录或Token已过期，请重新登录");
        }

        Object details = authentication.getDetails();
        if (details instanceof Long userId) {
            return userId;
        }

        // 兼容 WebAuthenticationDetails 的情况（未设置 userId 到 details）
        throw new BusinessException(401, "无法获取当前用户信息，请重新登录");
    }
}
