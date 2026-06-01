package com.iot.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 认证过滤器
 * <p>
 * 继承 OncePerRequestFilter，确保每个请求只执行一次过滤。
 * 从请求头中提取 JWT Token 并进行校验，认证通过后将用户信息
 * 设置到 SecurityContext 中，供后续的授权判断使用。
 * </p>
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 需要跳过 Token 校验的路径列表 */
    private static final List<String> PERMITTED_PATHS = List.of("/api/user/register", "/api/user/login");

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * 过滤器核心逻辑
     * <p>
     * 1. 检查请求路径是否在放行列表中，是则直接放行
     * 2. 从 Authorization header 中提取 Bearer Token
     * 3. 解析并验证 Token 的有效性，提取 userId 和 phone
     * 4. 从 Token 中提取用户名，加载用户信息
     * 5. 将认证信息（含 userId）设置到 SecurityContext 中
     * </p>
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      I/O 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // 对放行路径跳过 Token 校验
        if (PERMITTED_PATHS.contains(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 从请求头中提取 Token
            String token = extractToken(request);

            if (StringUtils.hasText(token)) {
                // 解析 Token 获取用户名和 userId
                Claims claims = parseToken(token);
                String username = claims.getSubject();
                Long userId = claims.get("userId", Long.class);

                if (StringUtils.hasText(username)
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // 加载用户详细信息
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // 创建认证令牌，将 userId 存入 details
                    // SecurityUtil.getCurrentUserId() 通过读取 details 获取 userId
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(userId);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.warn("JWT Token 认证失败: {}", e.getMessage());
            // Token 无效时不清空上下文，让 AuthenticationEntryPoint 处理即可
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 Bearer Token
     * <p>
     * 从 Authorization 请求头中提取 Bearer 类型的 Token 字符串。
     * 格式: "Authorization: Bearer &lt;token&gt;"
     * </p>
     *
     * @param request HTTP 请求
     * @return Token 字符串，不存在或格式错误时返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 解析 JWT Token 并返回 Claims
     * <p>
     * 使用 JJWT 库解析 JWT Token，验证签名。
     * 使用 HMAC-SHA 算法，密钥为配置中的 jwt.secret。
     * </p>
     *
     * @param token JWT Token
     * @return Token 中的 Claims（含 subject=phone, userId）
     * @throws io.jsonwebtoken.JwtException Token 无效或已过期时抛出
     */
    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
