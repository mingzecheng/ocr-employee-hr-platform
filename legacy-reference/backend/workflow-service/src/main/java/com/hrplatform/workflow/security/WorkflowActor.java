package com.hrplatform.workflow.security;

import com.hrplatform.common.security.DataScope;
import com.hrplatform.common.security.JwtPrincipal;
import org.springframework.security.core.Authentication;

public record WorkflowActor(long userId, DataScope scope, String bearerToken) {
    public WorkflowActor {
        if (scope == null) {
            scope = DataScope.none(userId);
        }
    }

    public static WorkflowActor from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new IllegalStateException("Authenticated principal is required");
        }
        String token = authentication.getCredentials() instanceof String value ? value : null;
        return new WorkflowActor(principal.userId(), DataScope.fromPrincipal(principal), token);
    }
}
