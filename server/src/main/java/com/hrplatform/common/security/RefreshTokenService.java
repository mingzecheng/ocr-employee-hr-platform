package com.hrplatform.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Refresh token（7 天免登录）存储。
 *
 * <p>设计要点：
 * <ul>
 *   <li>refresh token 是随机 UUID，<b>不放进 JWT</b>，泄露后无法被用来伪造 access token；</li>
 *   <li>Redis key {@code auth:refresh:&lt;token&gt;} → userId 字符串，TTL 7 天（{@code Duration.ofDays(7)}）；</li>
 *   <li>每次 /refresh 都 rotate：旧 token 立即删除，签发新 token，滑动续期；</li>
 *   <li>降级策略：Redis 不可用时，{@link #issue} 返回 {@code null}（登录照常成功，只是不发 refreshToken），
 *       但 {@link #rotate} 抛 {@link RefreshTokenUnavailableException}（映射 503，前端跳登录页）。</li>
 * </ul>
 */
@Service
public class RefreshTokenService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String KEY_PREFIX = "auth:refresh:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public RefreshTokenService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** rotate 成功的结果：所属用户 + 新的 refresh token。 */
    public record RotatedRefreshToken(Long userId, String refreshToken) {
    }

    /**
     * 为用户签发一个新的 refresh token。Redis 不可用时返回 {@code null}（登录仍可成功，只是不支持免登录续期）。
     */
    public String issue(Long userId) {
        if (userId == null) {
            return null;
        }
        if (redisTemplate == null) {
            log.warn("Redis 不可用（未配置 StringRedisTemplate），本次登录不签发 refresh token");
            return null;
        }
        try {
            String token = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId), TTL);
            return token;
        } catch (RuntimeException ex) {
            log.warn("Redis 命令失败，refresh token 签发降级跳过：{}", ex.getMessage());
            return null;
        }
    }

    /**
     * 校验并 rotate 旧 refresh token：删除旧 key、签发新 key。
     *
     * @throws InvalidRefreshTokenException       token 不存在/已过期/已被使用过
     * @throws RefreshTokenUnavailableException   Redis 不可用，无法完成校验
     */
    public RotatedRefreshToken rotate(String oldToken) {
        if (oldToken == null || oldToken.isBlank()) {
            throw new InvalidRefreshTokenException("refresh token 不能为空");
        }
        if (redisTemplate == null) {
            log.warn("Redis 不可用（未配置 StringRedisTemplate），无法校验 refresh token");
            throw new RefreshTokenUnavailableException("refresh token 服务暂不可用", null);
        }
        String key = KEY_PREFIX + oldToken;
        try {
            String userIdValue = redisTemplate.opsForValue().get(key);
            if (userIdValue == null) {
                throw new InvalidRefreshTokenException("refresh token 无效或已过期");
            }
            Long userId = Long.valueOf(userIdValue);
            String newToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(KEY_PREFIX + newToken, userIdValue, TTL);
            // 一次性使用：旧 token rotate 后立即失效。
            redisTemplate.delete(key);
            return new RotatedRefreshToken(userId, newToken);
        } catch (InvalidRefreshTokenException rethrow) {
            throw rethrow;
        } catch (RuntimeException ex) {
            log.warn("Redis 命令失败，refresh token 校验失败：{}", ex.getMessage());
            throw new RefreshTokenUnavailableException("refresh token 服务暂不可用", ex);
        }
    }

    /** 登出时删除 refresh token。Redis 不可用仅打 warn，不影响登出接口成功。 */
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (redisTemplate == null) {
            log.warn("Redis 不可用（未配置 StringRedisTemplate），refresh token 删除已跳过");
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + token);
        } catch (RuntimeException ex) {
            log.warn("Redis 命令失败，refresh token 删除降级跳过：{}", ex.getMessage());
        }
    }
}
