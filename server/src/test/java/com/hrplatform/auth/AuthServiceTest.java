package com.hrplatform.auth;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private SysUserMapper userMapper;

    @Test
    void validCredentialsReturnJwtAndUserScope() {
        when(userMapper.findByUsername("hr-admin")).thenReturn(new SysUser(
                10L, "hr-admin", new BCryptPasswordEncoder().encode("secret"),
                100L, 7L, true, Set.of("HR_ADMIN")
        ));

        AuthService service = new AuthService(
                userMapper,
                new BCryptPasswordEncoder(),
                new JwtService("test-secret-key-test-secret-key-test-secret-key", Duration.ofHours(8))
        );

        AuthResponse response = service.login(new LoginRequest("hr-admin", "secret"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().username()).isEqualTo("hr-admin");
        assertThat(response.user().dataScope()).isEqualTo("ALL");
    }

    @Test
    void invalidCredentialsUseStableBusinessException() {
        when(userMapper.findByUsername("hr-admin")).thenReturn(null);
        AuthService service = new AuthService(
                userMapper,
                new BCryptPasswordEncoder(),
                new JwtService("test-secret-key-test-secret-key-test-secret-key", Duration.ofHours(8))
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.login(new LoginRequest("hr-admin", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("用户名或密码错误");
    }
}
