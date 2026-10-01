package com.hrplatform.auth;

import java.util.Set;

public record SysUser(
        Long id,
        String username,
        String passwordHash,
        Long employeeId,
        Long departmentId,
        boolean enabled,
        Set<String> roles
) {
}
