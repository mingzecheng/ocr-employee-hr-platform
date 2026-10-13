package com.hrplatform.common.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 审批幂等键。底层用 Redis {@code SET key value NX EX ttl}（{@code setIfAbsent}）实现：
 * key 不存在才写入成功并返回 true；已存在说明窗口内重复提交，返回 false。
 *
 * <p>降级策略：Redis 不可用（无 bean 或连接失败）时放行（返回 true）并打 warn 日志，
 * 保证本地无 Redis / CI 环境既能启动也不阻断业务，幂等仅在 Redis 可用时生效。
 */
@Service
public class IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public IdempotencyService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试占有幂等键。
     *
     * @return true 表示首次提交，调用方应继续执行；false 表示窗口内重复提交，调用方应拒绝。
     */
    public boolean tryAcquire(String key, Duration ttl) {
        if (redisTemplate == null) {
            log.warn("Redis 不可用（未配置 StringRedisTemplate），幂等检查已放行 key={}", key);
            return true;
        }
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException ex) {
            log.warn("Redis 命令失败，幂等检查降级放行 key={}：{}", key, ex.getMessage());
            return true;
        }
    }
}
