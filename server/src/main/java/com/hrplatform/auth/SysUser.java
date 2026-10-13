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
    public SysUser(Long id, String username, String passwordHash, Long employeeId, Long departmentId,
                   boolean enabled) {
        this(id, username, passwordHash, employeeId, departmentId, enabled, Set.of());
    }

    public SysUser withRoles(Set<String> assignedRoles) {
        return new SysUser(id, username, passwordHash, employeeId, departmentId, enabled, assignedRoles);
    }
}
