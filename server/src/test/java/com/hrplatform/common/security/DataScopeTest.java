package com.hrplatform.common.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeTest {
    @Test
    void systemAdminCanAccessEveryEmployee() {
        DataScope scope = DataScope.fromClaims(1L, null, null, Set.of("SYSTEM_ADMIN"));

        assertThat(scope.type()).isEqualTo(DataScope.Type.ALL);
        assertThat(scope.canAccess(42L, 99L)).isTrue();
    }

    @Test
    void departmentManagerCanAccessOnlyTheirDepartment() {
        DataScope scope = DataScope.fromClaims(2L, 20L, 7L, Set.of("DEPT_MANAGER"));

        assertThat(scope.type()).isEqualTo(DataScope.Type.DEPARTMENT);
        assertThat(scope.canAccess(30L, 7L)).isTrue();
        assertThat(scope.canAccess(30L, 8L)).isFalse();
    }

    @Test
    void employeeCanAccessOnlyTheirOwnRecord() {
        DataScope scope = DataScope.fromClaims(3L, 30L, 7L, Set.of("EMPLOYEE"));

        assertThat(scope.type()).isEqualTo(DataScope.Type.EMPLOYEE);
        assertThat(scope.canAccess(30L, 7L)).isTrue();
        assertThat(scope.canAccess(31L, 7L)).isFalse();
    }

    @Test
    void missingClaimsProduceNoScope() {
        DataScope scope = DataScope.fromClaims(null, null, null, Set.of());

        assertThat(scope.type()).isEqualTo(DataScope.Type.NONE);
        assertThat(scope.canAccess(1L, 1L)).isFalse();
    }
}
