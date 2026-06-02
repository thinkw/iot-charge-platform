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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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

    /**
     * 过滤器核心逻辑
     * <p>
     * 1. 检查请求路径是否在放行列表中，是则直接放行
     * 2. 从 Authorization header 中提取 Bearer Token
     * 3. 解析 JWT Token，验证签名和有效期
     * 4. 从 Token Claims 中提取 userId、phone 和 roles（无需查 DB）
     * 5. 构建认证令牌，将 userId 存入 details 供 SecurityUtil 提取
     * </p>
     * <p>
     * 角色信息从 JWT Token 直接获取，避免每次请求都查询数据库。
     * Token 签名保证了 roles 不可篡改，角色变更通过 Token 过期自然生效。
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
                // 解析 Token 获取 userId、phone 和 roles
                Claims claims = parseToken(token);
                String phone = claims.getSubject();
                Long userId = claims.get("userId", Long.class);

                if (StringUtils.hasText(phone) && userId != null
                        && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // 从 JWT Token Claims 中直接提取角色，无需查询数据库
                    @SuppressWarnings("unchecked")
                    List<String> roles = claims.get("roles", List.class);
                    if (roles == null || roles.isEmpty()) {
                        roles = List.of("ROLE_USER");
                    }
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    // 创建认证令牌：principal=phone，authorities=角色列表
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(phone, null, authorities);
                    // 将 userId 存入 details，SecurityUtil.getCurrentUserId() 从中提取
                    authentication.setDetails(userId);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.warn("JWT Token 认证失败: {}", e.getMessage());
            // Token 无效/过期时清理残留的认证上下文，防止旧认证信息泄露
            SecurityContextHolder.clearContext();
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
