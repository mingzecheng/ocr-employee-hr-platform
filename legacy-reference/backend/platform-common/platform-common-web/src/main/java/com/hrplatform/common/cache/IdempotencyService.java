package com.hrplatform.common.cache;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class IdempotencyService {

    private final StringRedisTemplate redis;

    public IdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean claim(String requestId, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        String key = CacheKeys.idempotent(requestId);
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
    }

    public void release(String requestId) {
        redis.delete(CacheKeys.idempotent(requestId));
    }
}
