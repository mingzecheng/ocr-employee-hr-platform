package com.hrplatform.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    @Test
    void issuedTokenRestoresAuthorizationClaims() {
        JwtService service = new JwtService(
                "test-secret-key-test-secret-key-test-secret-key",
                Duration.ofHours(8)
        );
        JwtPrincipal input = new JwtPrincipal(11L, 22L, 7L, Set.of("HR_ADMIN"));

        String token = service.issue(input);
        JwtPrincipal output = service.parse(token);

        assertThat(output.userId()).isEqualTo(11L);
        assertThat(output.employeeId()).isEqualTo(22L);
        assertThat(output.departmentId()).isEqualTo(7L);
        assertThat(output.roles()).containsExactly("HR_ADMIN");
    }

    @Test
    void invalidTokenIsRejected() {
        JwtService service = new JwtService(
                "test-secret-key-test-secret-key-test-secret-key",
                Duration.ofHours(8)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.parse("invalid.token"))
                .isInstanceOf(JwtAuthenticationException.class);
    }
}
