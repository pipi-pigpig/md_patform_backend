package com.mdplatform.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT令牌提供者，负责JWT令牌的生成、解析和验证
 *
 * <p>功能：
 *     1. 生成JWT访问令牌
 *     2. 从令牌中提取用户信息（用户名、用户ID）
 *     3. 验证令牌的有效性
 * </p>
 *
 * <p>配置要求：
 *     必须在application.yml中配置jwt.secret属性，不允许使用默认值。
 *     示例配置：
 *     jwt:
 *       secret: your-secure-secret-key-at-least-256-bits-long
 *       expiration: 86400000
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Component
@Slf4j
public class JwtTokenProvider {

    /** JWT签名密钥，必须通过配置文件注入，不允许使用默认值 */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /** JWT令牌过期时间（毫秒） */
    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    /** HMAC签名密钥对象 */
    private Key key;

    /**
     * 初始化方法 - 在Bean创建后验证配置并生成签名密钥
     *
     * <p>验证jwt.secret是否已正确配置，若未配置则抛出异常阻止应用启动</p>
     *
     * @throws IllegalStateException 如果jwt.secret未配置或为空
     */
    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("jwt.secret 未配置！必须在application.yml中设置jwt.secret属性");
        }
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * 生成JWT访问令牌
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return 生成的JWT令牌字符串
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从JWT令牌中提取用户名
     *
     * @param token JWT令牌
     * @return 令牌中包含的用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    /**
     * 从JWT令牌中提取用户ID
     *
     * @param token JWT令牌
     * @return 令牌中包含的用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("userId", Long.class);
    }

    /**
     * 验证JWT令牌的有效性
     *
     * @param token 待验证的JWT令牌
     * @return true表示令牌有效，false表示令牌无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (MalformedJwtException ex) {
            log.error("无效的 JWT Token");
        } catch (ExpiredJwtException ex) {
            log.error("JWT Token 已过期");
        } catch (UnsupportedJwtException ex) {
            log.error("不支持的 JWT Token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT Token 为空");
        }
        return false;
    }

    /**
     * 获取JWT令牌过期时间配置
     *
     * @return 过期时间（毫秒）
     */
    public long getExpirationTime() {
        return jwtExpiration;
    }
}
