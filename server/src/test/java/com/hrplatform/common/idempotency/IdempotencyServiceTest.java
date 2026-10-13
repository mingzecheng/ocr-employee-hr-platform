package com.hrplatform.common.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class IdempotencyServiceTest {

    @SuppressWarnings("unchecked")
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private IdempotencyService newService() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return new IdempotencyService(redisTemplate);
    }

    @Test
    void setIfAbsentSucceedsAllowsFirstSubmission() {
        when(valueOps.setIfAbsent(eq("idem:approve:9:10"), eq("1"), eq(Duration.ofSeconds(10))))
                .thenReturn(true);
        IdempotencyService service = newService();

        assertThat(service.tryAcquire("idem:approve:9:10", Duration.ofSeconds(10))).isTrue();
        verify(valueOps).setIfAbsent("idem:approve:9:10", "1", Duration.ofSeconds(10));
    }

    @Test
    void setIfAbsentFailsBlocksRepeatedSubmission() {
        when(valueOps.setIfAbsent(eq("idem:approve:9:10"), eq("1"), eq(Duration.ofSeconds(10))))
                .thenReturn(false);
        IdempotencyService service = newService();

        assertThat(service.tryAcquire("idem:approve:9:10", Duration.ofSeconds(10))).isFalse();
    }

    @Test
    void redisDownDegradesToAllow() {
        when(valueOps.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("Unable to connect to Redis"));
        IdempotencyService service = newService();

        // 无 Redis 环境下降级放行，不能让审批因 Redis 不可用而整体失败
        assertThat(service.tryAcquire("idem:approve:9:10", Duration.ofSeconds(10))).isTrue();
    }

    @Test
    void noRedisTemplateBeanDegradesToAllow() {
        IdempotencyService service = new IdempotencyService(null);

        assertThat(service.tryAcquire("idem:approve:9:10", Duration.ofSeconds(10))).isTrue();
    }
}
