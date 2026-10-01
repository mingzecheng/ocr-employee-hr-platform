package com.hrplatform.auth;

import com.hrplatform.common.security.DataScope;

import java.util.Set;

public record AuthUser(
        Long id,
        String username,
        Long employeeId,
        Long departmentId,
        Set<String> roles,
        String dataScope
) {
    public static AuthUser from(SysUser user) {
        DataScope scope = DataScope.fromClaims(user.id(), user.employeeId(), user.departmentId(), user.roles());
        return new AuthUser(user.id(), user.username(), user.employeeId(), user.departmentId(), user.roles(), scope.type().name());
    }
}
