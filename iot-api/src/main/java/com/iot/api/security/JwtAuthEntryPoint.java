package com.iot.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.common.model.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 认证入口点
 * <p>
 * 当未认证的用户访问需要认证的资源时，此组件负责返回 401 未授权响应。
 * 使用统一的 Result 格式返回 JSON 数据，便于前端统一处理。
 * </p>
 */
@Slf4j
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    /**
     * 认证失败处理
     * <p>
     * 当请求未通过认证时，返回 401 状态码和统一格式的错误响应。
     * 响应体使用 Result.error() 构建，包含错误码和提示信息。
     * </p>
     *
     * @param request       导致认证失败的 HTTP 请求
     * @param response      将被返回的 HTTP 响应
     * @param authException 认证异常信息
     * @throws IOException 写入响应时可能发生的 I/O 异常
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("认证失败 - URI: {}, 原因: {}", request.getRequestURI(), authException.getMessage());

        response.setContentType("application/json;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 使用统一 Result 格式返回 401 错误
        Result<?> result = Result.error(401, "未授权，请先登录");
        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
