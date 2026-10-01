package com.hrplatform.common.security;

import java.util.List;

public record JwtPrincipal(long userId,
                           String username,
                           List<String> roles,
                           List<String> permissions,
                           Long employeeId,
                           Long departmentId) {

    public JwtPrincipal(long userId,
                        String username,
                        List<String> roles,
                        List<String> permissions,
                        Long employeeId) {
        this(userId, username, roles, permissions, employeeId, null);
    }

    public JwtPrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
