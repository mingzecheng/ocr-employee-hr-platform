package com.hrplatform.admin;

import com.hrplatform.auth.SysUser;
import com.hrplatform.auth.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 在线用户管理：基于 Redis 中的 refresh token（{@code auth:refresh:<uuid>} -> userId，TTL 7 天）
 * 反推当前在线用户，并支持强制下线。
 *
 * <p>降级策略：Redis 不可用（无 bean 或命令失败）时列表返回空、强制下线空操作，不抛异常。
 * 强制下线仅删除 refresh token——access token 在其自然过期（默认 8 小时）前仍有效，
 * 但用户已无法续期，达到"踢下线"的目的。
 */
@Service
public class OnlineUserService {
    private static final Logger log = LoggerFactory.getLogger(OnlineUserService.class);
    private static final String KEY_PREFIX = "auth:refresh:";
    private static final long REFRESH_TTL_SECONDS = TimeUnit.DAYS.toSeconds(7);

    private final StringRedisTemplate redisTemplate;
    private final SysUserMapper userMapper;

    @Autowired
    public OnlineUserService(@Autowired(required = false) StringRedisTemplate redisTemplate,
                            SysUserMapper userMapper) {
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
    }

    /** 扫描所有 refresh token，按 userId 聚合返回在线用户。Redis 不可用返回空列表。 */
    public List<OnlineUserRow> listOnlineUsers() {
        if (redisTemplate == null) {
            return List.of();
        }
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }
            // userId -> 该用户剩余 TTL 最大的 key（最接近最近一次登录）
            Map<Long, Long> userIdToMostRecentRemaining = new LinkedHashMap<>();
            for (String key : keys) {
                String value = redisTemplate.opsForValue().get(key);
                if (value == null || value.isBlank()) {
                    continue;
                }
                Long userId = parseUserId(value);
                if (userId == null) {
                    continue;
                }
                Long remaining = ttlSeconds(key);
                userIdToMostRecentRemaining.merge(userId, remaining, Math::max);
            }

            List<OnlineUserRow> rows = new ArrayList<>();
            for (Map.Entry<Long, Long> entry : userIdToMostRecentRemaining.entrySet()) {
                Long userId = entry.getKey();
                SysUser user = safeFindUser(userId);
                String username = user == null ? "user#" + userId : user.username();
                Set<String> roles = user == null ? Set.of() : safeFindRoles(userId);
                rows.add(new OnlineUserRow(userId, username, roles, loginApprox(entry.getValue())));
            }
            rows.sort(Comparator.comparing(OnlineUserRow::userId));
            return rows;
        } catch (RuntimeException ex) {
            log.warn("在线用户列表采集降级：{}", ex.getMessage());
            return List.of();
        }
    }

    /** 删除该用户所有 refresh token，使其无法续期。返回删除的 token 数量。 */
    public int forceOffline(Long userId) {
        if (redisTemplate == null || userId == null) {
            return 0;
        }
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            List<String> owned = new ArrayList<>();
            for (String key : keys) {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null && value.trim().equals(String.valueOf(userId))) {
                    owned.add(key);
                }
            }
            if (owned.isEmpty()) {
                return 0;
            }
            redisTemplate.delete(owned);
            return owned.size();
        } catch (RuntimeException ex) {
            log.warn("强制下线降级：{}", ex.getMessage());
            return 0;
        }
    }

    private Long ttlSeconds(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl == null ? -1 : ttl;
        } catch (RuntimeException ex) {
            return -1L;
        }
    }

    /** 由剩余 TTL 反推近似登录时间：elapsed = 7d - remaining。 */
    private String loginApprox(Long remainingSeconds) {
        long elapsed = remainingSeconds == null || remainingSeconds < 0
                ? 0 : Math.max(0, REFRESH_TTL_SECONDS - remainingSeconds);
        return Instant.now().minusSeconds(elapsed).toString();
    }

    private Long parseUserId(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private SysUser safeFindUser(Long userId) {
        try {
            return userMapper.findById(userId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Set<String> safeFindRoles(Long userId) {
        try {
            Set<String> roles = userMapper.findRoles(userId);
            return roles == null ? Set.of() : roles;
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }
}
