package com.hrplatform.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * JWT 登出黑名单。登出时把 token 的 SHA-256 摘要写入 Redis，key = {@code auth:blacklist:<sha256hex>}，
 * TTL = token 剩余有效期，过期自动清除。
 *
 * <p>降级策略：Redis 不可用（无 bean 或连接失败）时查黑名单一律返回 false（放行），
 * 保证本地无 Redis / CI 环境既能启动也不阻断登录。
 */
@Service
public class TokenBlacklistService {
    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String KEY_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtService jwtService;

    @Autowired
    public TokenBlacklistService(@Autowired(required = false) StringRedisTemplate redisTemplate,
                                 JwtService jwtService) {
        this.redisTemplate = redisTemplate;
        this.jwtService = jwtService;
    }

    /** 将 token 的 SHA-256 摘要写入黑名单，TTL = token 剩余有效期。幂等：重复写入覆盖即可。 */
    public void invalidate(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (redisTemplate == null) {
            log.warn("Redis 不可用（未配置 StringRedisTemplate），登出黑名单写入已跳过");
            return;
        }
        try {
            String key = KEY_PREFIX + sha256Hex(token);
            Duration ttl = jwtService.remainingTtl(token);
            redisTemplate.opsForValue().set(key, "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("Redis 命令失败，登出黑名单写入降级跳过：{}", ex.getMessage());
        }
    }

    /** 返回 true 表示该 token 已被登出。Redis 不可用时返回 false（放行）。 */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (redisTemplate == null) {
            log.warn("Redis 不可用（未配置 StringRedisTemplate），黑名单检查已放行");
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + sha256Hex(token)));
        } catch (RuntimeException ex) {
            log.warn("Redis 命令失败，黑名单检查降级放行：{}", ex.getMessage());
            return false;
        }
    }

    /** SHA-256 hex 摘要（小写）。 */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
