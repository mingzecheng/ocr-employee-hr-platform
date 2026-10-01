package com.hrplatform.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void jwtPreservesDepartmentContext() {
        JwtService service = new JwtService(
                "12345678901234567890123456789012", "test", Duration.ofMinutes(5));

        String token = service.issue(7L, "manager", List.of("DEPT_MANAGER"), List.of(), 42L, 12L).value();

        JwtPrincipal principal = service.parse(token);
        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.employeeId()).isEqualTo(42L);
        assertThat(principal.departmentId()).isEqualTo(12L);
    }
}
