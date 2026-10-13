package com.hrplatform.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JWT 登出黑名单单元测试。key = {@code auth:blacklist:<sha256(token)>}，TTL = token 剩余有效期。
 * Redis 不可用（无 bean / 连接失败）时降级，不阻塞登录与登出。
 */
class TokenBlacklistServiceTest {

    private static final String SECRET = "test-secret-key-test-secret-key-test-secret-key";

    @SuppressWarnings("unchecked")
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final JwtService jwtService = new JwtService(SECRET, Duration.ofHours(8));

    private TokenBlacklistService newService() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return new TokenBlacklistService(redisTemplate, jwtService);
    }

    private String sampleToken() {
        return jwtService.issue(new JwtPrincipal(11L, 22L, 7L, Set.of("HR_ADMIN")));
    }

    @Test
    void invalidateWritesSha256KeyWithRemainingTtl() {
        TokenBlacklistService service = newService();
        String token = sampleToken();

        service.invalidate(token);

        String expectedKey = "auth:blacklist:" + TokenBlacklistService.sha256Hex(token);
        org.mockito.ArgumentCaptor<Duration> ttlCaptor =
                org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq(expectedKey), eq("1"), ttlCaptor.capture());
        // TTL 应约等于 token 剩余 8h（秒级截断），允许 5s 时钟漂移
        assertThat(ttlCaptor.getValue())
                .isBetween(Duration.ofHours(8).minusSeconds(5), Duration.ofHours(8).plusSeconds(5));
    }

    @Test
    void invalidateIsIdempotentDoesNotThrowOnRepeat() {
        TokenBlacklistService service = newService();
        String token = sampleToken();

        service.invalidate(token);
        service.invalidate(token); // 重复登出不应报错，覆盖写即可

        String expectedKey = "auth:blacklist:" + TokenBlacklistService.sha256Hex(token);
        verify(valueOps, times(2)).set(eq(expectedKey), eq("1"),
                org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void blacklistHitReportsTrue() {
        TokenBlacklistService service = newService();
        String token = sampleToken();
        String key = "auth:blacklist:" + TokenBlacklistService.sha256Hex(token);
        when(redisTemplate.hasKey(key)).thenReturn(true);

        assertThat(service.isBlacklisted(token)).isTrue();
    }

    @Test
    void redisDownOnCheckDegradesToNotBlacklisted() {
        TokenBlacklistService service = newService();
        String token = sampleToken();
        when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("Unable to connect to Redis"));

        // 降级放行：Redis 挂了不能把所有请求都挡掉
        assertThat(service.isBlacklisted(token)).isFalse();
    }

    @Test
    void noRedisTemplateBeanDegrades() {
        TokenBlacklistService service = new TokenBlacklistService(null, jwtService);
        String token = sampleToken();

        assertThat(service.isBlacklisted(token)).isFalse();
        service.invalidate(token); // 不应抛异常
    }

    @Test
    void sha256HexIsStableHexDigest() {
        assertThat(TokenBlacklistService.sha256Hex("abc")).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
