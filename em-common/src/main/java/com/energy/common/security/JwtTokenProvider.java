package com.energy.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT 签发与校验。
 *
 * <p>放在公共模块，因为签发方（业务服务的登录接口）和校验方（网关过滤器）需要
 * 对同一个令牌达成一致——两边各写一份实现，迟早会因为某处改了算法或声明名而对不上。
 *
 * <p><b>算法与密钥：</b>HS256 要求密钥长度不少于 256 位（32 字节），
 * 否则 {@link Keys#hmacShaKeyFor} 会直接抛 {@code WeakKeyException}。
 * 这是最常见的一个坑——密钥配短了，启动即失败。
 *
 * <p><b>JWT 的固有缺陷：</b>签发后服务端无法主动作废。
 * 要支持「登出立即失效」或「禁用账号立即生效」，需要引入 Redis 黑名单，
 * 或像本项目这样在每次请求时按用户主键回查一次状态。这里选择了后者。
 */
public final class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NICKNAME = "nickname";

    private JwtTokenProvider() {
    }

    private static SecretKey keyOf(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发令牌。
     *
     * @param secret        密钥，长度必须 ≥ 32 字节
     * @param userId        用户主键，写入 subject
     * @param role          角色，写入自定义声明
     * @param nickname      昵称，写入自定义声明
     * @param expireMinutes 有效期（分钟）
     */
    public static String issue(String secret, long userId, String role,
                               String nickname, long expireMinutes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_NICKNAME, nickname)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(expireMinutes))))
                .signWith(keyOf(secret))
                .compact();
    }

    /**
     * 校验并解析令牌。
     *
     * @throws JwtException 签名不匹配、已过期、格式非法时抛出
     */
    public static Claims parse(String secret, String token) {
        return Jwts.parser()
                .verifyWith(keyOf(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 Authorization 头中取出 Bearer 令牌，取不到返回 null */
    public static String resolveToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        String token = authorizationHeader.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** 便捷方法：把令牌中的用户信息整理成 Map，便于透传给下游 */
    public static Map<String, String> toHeaders(Claims claims) {
        return Map.of(
                "X-User-Id", String.valueOf(claims.getSubject()),
                "X-User-Role", String.valueOf(claims.get(CLAIM_ROLE, String.class)),
                "X-User-Name", String.valueOf(claims.get(CLAIM_NICKNAME, String.class)));
    }
}
