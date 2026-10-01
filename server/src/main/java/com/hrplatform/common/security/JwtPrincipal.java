package com.hrplatform.common.security;

import java.util.Set;

public record JwtPrincipal(Long userId, Long employeeId, Long departmentId, Set<String> roles) {
    public DataScope dataScope() {
        return DataScope.fromClaims(userId, employeeId, departmentId, roles);
    }
}
