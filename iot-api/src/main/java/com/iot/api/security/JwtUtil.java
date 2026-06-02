package com.iot.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT Token 工具类
 * <p>
 * 负责 JWT Token 的生成与解析。
 * Token 载荷中包含 userId（claims）和 phone（subject），
 * 用于在 JwtAuthFilter 中还原用户身份信息。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
public class JwtUtil {

    /** JWT 签名密钥，来自配置文件 jwt.secret */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Token 过期时间（毫秒），来自配置文件 jwt.expiration，默认 24 小时 */
    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    /**
     * 生成 JWT Token
     * <p>
     * subject = phone，claims 中包含 userId 和 roles（角色编码列表），签名算法为 HMAC-SHA256。
     * </p>
     *
     * @param userId 用户ID
     * @param phone  用户手机号
     * @param roles  用户角色编码列表（如 ["ROLE_USER", "ROLE_ADMIN"]），可选
     * @return JWT Token 字符串
     */
    public String generateToken(Long userId, String phone, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtExpiration);

        var builder = Jwts.builder()
                .subject(phone)
                .claim("userId", userId)
                .claim("roles", roles != null ? roles : List.of("ROLE_USER"))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key);

        String token = builder.compact();

        log.debug("[JWT] 生成 Token 成功 - userId: {}, phone: {}, roles: {}", userId, phone, roles);
        return token;
    }

    /**
     * 生成 JWT Token（兼容旧调用，默认 ROLE_USER）
     *
     * @param userId 用户ID
     * @param phone  用户手机号
     * @return JWT Token 字符串
     */
    public String generateToken(Long userId, String phone) {
        return generateToken(userId, phone, List.of("ROLE_USER"));
    }

    /**
     * 解析 JWT Token 并返回所有 Claims
     * <p>
     * 验证 Token 签名和有效期，解析失败会抛出异常供调用方处理。
     * </p>
     *
     * @param token JWT Token 字符串
     * @return Token 中的 Claims
     * @throws io.jsonwebtoken.JwtException Token 无效或已过期时抛出
     */
    public Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中提取用户ID
     *
     * @param token JWT Token 字符串
     * @return 用户ID，解析失败返回 null
     */
    public Long extractUserId(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            log.warn("[JWT] 提取 userId 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中提取手机号（subject）
     *
     * @param token JWT Token 字符串
     * @return 手机号，解析失败返回 null
     */
    public String extractPhone(String token) {
        try {
            return parseToken(token).getSubject();
        } catch (Exception e) {
            log.warn("[JWT] 提取 phone 失败: {}", e.getMessage());
            return null;
        }
    }
}
