package com.hrplatform.common.cache;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String response = connection.ping();
            if ("PONG".equalsIgnoreCase(response)) {
                return Health.up().withDetail("status", "PONG").build();
            }
            return Health.down().withDetail("status", "unexpected_response").build();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("status", "unavailable").build();
        }
    }
}
