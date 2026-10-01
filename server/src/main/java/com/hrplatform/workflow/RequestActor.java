package com.hrplatform.workflow;

import com.hrplatform.common.security.DataScope;

import java.util.Set;

public record RequestActor(Long userId, Set<String> roles, DataScope scope) {
    public RequestActor {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
