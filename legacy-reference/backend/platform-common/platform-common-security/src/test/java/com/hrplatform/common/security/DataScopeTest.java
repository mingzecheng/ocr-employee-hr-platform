package com.hrplatform.common.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeTest {

    @Test
    void managersUseDepartmentScopeAndEmployeesUseOwnEmployeeScope() {
        DataScope managerScope = DataScope.fromPrincipal(new JwtPrincipal(
                1L, "manager", List.of("DEPT_MANAGER"), List.of(), 9L, 12L));
        DataScope employeeScope = DataScope.fromPrincipal(new JwtPrincipal(
                2L, "employee", List.of("EMPLOYEE"), List.of(), 9L, null));

        assertThat(managerScope.type()).isEqualTo(DataScope.Type.DEPARTMENT);
        assertThat(managerScope.allowsEmployee(88L, 12L)).isTrue();
        assertThat(managerScope.allowsEmployee(89L, 13L)).isFalse();
        assertThat(employeeScope.type()).isEqualTo(DataScope.Type.EMPLOYEE);
        assertThat(employeeScope.allowsEmployee(9L, 88L)).isTrue();
        assertThat(employeeScope.allowsEmployee(10L, 12L)).isFalse();
    }

    @Test
    void administratorsHaveGlobalScopeAndIncompleteContextHasNoScope() {
        DataScope adminScope = DataScope.fromPrincipal(new JwtPrincipal(
                1L, "admin", List.of("SYSTEM_ADMIN"), List.of(), null, null));
        DataScope incompleteScope = DataScope.fromPrincipal(new JwtPrincipal(
                2L, "manager", List.of("DEPT_MANAGER"), List.of(), 9L, null));

        assertThat(adminScope.type()).isEqualTo(DataScope.Type.ALL);
        assertThat(adminScope.allowsEmployee(88L, 12L)).isTrue();
        assertThat(incompleteScope.type()).isEqualTo(DataScope.Type.NONE);
        assertThat(incompleteScope.cacheKey()).isEqualTo("none:user:2");
    }
}
