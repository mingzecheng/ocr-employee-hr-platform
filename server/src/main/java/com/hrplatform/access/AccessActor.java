package com.hrplatform.access;

import com.hrplatform.common.security.DataScope;

import java.util.Set;

public record AccessActor(Long userId, Set<String> roles, DataScope scope) {
    public AccessActor {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
