package com.hrplatform.common.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    @Test
    void onlyFirstRequestCanClaimTheSameIdempotencyKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("hr:idempotent:req-1"), eq("1"), any(Duration.class)))
                .thenReturn(true, false);

        IdempotencyService service = new IdempotencyService(redis);

        assertThat(service.claim("req-1", Duration.ofMinutes(10))).isTrue();
        assertThat(service.claim("req-1", Duration.ofMinutes(10))).isFalse();
    }

    @Test
    void rejectsBlankRequestIdAndNonPositiveTtl() {
        IdempotencyService service = new IdempotencyService(mock(StringRedisTemplate.class));

        assertThatThrownBy(() -> service.claim(" ", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.claim("req-1", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void releaseDeletesTheNamespacedKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        IdempotencyService service = new IdempotencyService(redis);

        service.release("req-1");

        verify(redis).delete("hr:idempotent:req-1");
    }
}
