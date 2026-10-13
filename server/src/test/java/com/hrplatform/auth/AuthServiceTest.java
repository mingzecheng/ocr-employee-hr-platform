package com.hrplatform.auth;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import com.hrplatform.audit.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OperationLogService operationLogService;

    @Test
    void validCredentialsReturnJwtAndUserScope() {
        when(userMapper.findByUsername("hr-admin")).thenReturn(new SysUser(
                10L, "hr-admin", new BCryptPasswordEncoder().encode("secret"),
                100L, 7L, true, Set.of("HR_ADMIN")
        ));
        when(userMapper.findRoles(10L)).thenReturn(Set.of("HR_ADMIN"));

        AuthService service = new AuthService(
                userMapper,
                new BCryptPasswordEncoder(),
                new JwtService("test-secret-key-test-secret-key-test-secret-key", Duration.ofHours(8)),
                true
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
                new JwtService("test-secret-key-test-secret-key-test-secret-key", Duration.ofHours(8)),
                true
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.login(new LoginRequest("hr-admin", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("用户名或密码错误");
    }

    @Test
    void demoAccountCannotAuthenticateWhenDemoAccountsAreDisabled() {
        AuthService service = new AuthService(
                userMapper,
                new BCryptPasswordEncoder(),
                new JwtService("test-secret-key-test-secret-key-test-secret-key", Duration.ofHours(8)),
                false
        );

        assertThatThrownBy(() -> service.login(new LoginRequest("demo-admin", "password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("用户名或密码错误");
        verifyNoInteractions(userMapper);
    }

    @Test
    void successfulAndFailedLoginWriteAuditRecordsWithoutExposingCredentials() {
        when(userMapper.findByUsername("hr-admin")).thenReturn(new SysUser(
                10L, "hr-admin", new BCryptPasswordEncoder().encode("secret"),
                100L, 7L, true));
        when(userMapper.findRoles(10L)).thenReturn(Set.of("HR_ADMIN"));
        AuthService service = new AuthService(userMapper, new BCryptPasswordEncoder(),
                new JwtService("test-secret-key-test-secret-key-test-secret-key", Duration.ofHours(8)),
                true, operationLogService);

        service.login(new LoginRequest("hr-admin", "secret"));
        assertThatThrownBy(() -> service.login(new LoginRequest("hr-admin", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        org.mockito.Mockito.verify(operationLogService).record(10L, "LOGIN", "SYS_USER", 10L, "SUCCESS");
        org.mockito.Mockito.verify(operationLogService).record(10L, "LOGIN", "SYS_USER", 10L, "FAILED");
    }
}
