package com.hrplatform.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    @Test
    void issueReturnsNullWhenRedisIsNotConfigured() {
        RefreshTokenService service = new RefreshTokenService(null);
        assertThat(service.issue(42L)).isNull();
    }

    @Test
    void issueStoresTokenWithSevenDayTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);
        RefreshTokenService service = new RefreshTokenService(redis);

        String token = service.issue(42L);

        assertThat(token).isNotBlank();
        verify(valueOps).set(eq("auth:refresh:" + token), eq("42"), eq(Duration.ofDays(7)));
    }

    @Test
    void rotateDeletesOldTokenAndIssuesNewOne() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:refresh:old-token")).thenReturn("42");
        RefreshTokenService service = new RefreshTokenService(redis);

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate("old-token");

        assertThat(rotated.userId()).isEqualTo(42L);
        assertThat(rotated.refreshToken()).isNotEqualTo("old-token");
        // 旧 token 一次性使用：rotate 后立即删除。
        verify(redis).delete("auth:refresh:old-token");
        verify(valueOps).set(eq("auth:refresh:" + rotated.refreshToken()), eq("42"), eq(Duration.ofDays(7)));
    }

    @Test
    void rotateRejectsUnknownTokenAsUnauthorized() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        RefreshTokenService service = new RefreshTokenService(redis);

        assertThatThrownBy(() -> service.rotate("forged-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateMapsMissingRedisToServiceUnavailable() {
        RefreshTokenService service = new RefreshTokenService(null);
        assertThatThrownBy(() -> service.rotate("any-token"))
                .isInstanceOf(RefreshTokenUnavailableException.class);
    }

    @Test
    void rotateMapsRedisFailureToServiceUnavailable() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Connection refused"));
        RefreshTokenService service = new RefreshTokenService(redis);

        assertThatThrownBy(() -> service.rotate("any-token"))
                .isInstanceOf(RefreshTokenUnavailableException.class);
    }

    @Test
    void revokeDeletesTokenAndIsSilentWhenRedisDown() {
        RefreshTokenService service = new RefreshTokenService(redis);
        service.revoke("token-to-revoke");
        verify(redis).delete("auth:refresh:token-to-revoke");

        RefreshTokenService noRedis = new RefreshTokenService(null);
        noRedis.revoke("token-to-revoke"); // 不抛异常，登出接口保持成功
        noRedis.revoke(null);              // 空 token 直接跳过
    }
}
