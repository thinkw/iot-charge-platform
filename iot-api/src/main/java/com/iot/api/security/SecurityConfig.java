package com.iot.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置
 * <p>
 * 配置 HTTP 安全策略、JWT 认证过滤器、密码编码器等。
 * 采用无状态会话管理（STATELESS），所有 API 请求通过 JWT Token 进行鉴权。
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, JwtAuthEntryPoint jwtAuthEntryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
    }

    /**
     * 安全过滤器链
     * <p>
     * 配置路径权限：
     * - /api/user/register 和 /api/user/login 允许匿名访问
     * - 其他 /api/** 路径需要认证
     * - 禁用 CSRF（使用 JWT，无需 CSRF 保护）
     * - 设置为无状态会话模式
     * - 添加 JWT 认证过滤器
     * </p>
     *
     * @param http HttpSecurity 安全构建器
     * @return SecurityFilterChain 安全过滤器链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（JWT 无状态认证不需要 CSRF 保护）
            .csrf(csrf -> csrf.disable())
            // 配置异常处理 — 未认证请求返回 401 JSON
            .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
            // 配置路径权限（按声明顺序匹配，首个命中生效）
            .authorizeHttpRequests(auth -> auth
                // 公开接口：注册和登录
                .requestMatchers("/api/user/register", "/api/user/login").permitAll()
                // CORS 预检请求放行（OPTIONS 请求不携带 Token）
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // WebSocket 握手请求放行（WebSocket 自身有鉴权逻辑）
                .requestMatchers("/ws/**").permitAll()
                // 管理端接口：需要 ADMIN 角色
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 其他 API 接口：需要认证
                .requestMatchers("/api/**").authenticated()
                // 其余请求一律拒绝
                .anyRequest().denyAll()
            )
            // 会话管理 — 无状态（不使用 Session）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 添加 JWT 认证过滤器（在 UsernamePasswordAuthenticationFilter 之前执行）
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码编码器
     * <p>
     * 使用 BCrypt 加密算法对用户密码进行哈希处理，保证密码存储安全。
     * </p>
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     * <p>
     * 从 AuthenticationConfiguration 中获取 AuthenticationManager，
     * 用于在登录接口中完成用户名密码认证。
     * </p>
     *
     * @param config 认证配置
     * @return AuthenticationManager 实例
     * @throws Exception 获取异常
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
