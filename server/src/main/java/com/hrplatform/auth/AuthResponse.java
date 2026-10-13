package com.hrplatform.auth;

import java.time.Instant;

public record AuthResponse(String accessToken, Instant expiresAt, AuthUser user, String refreshToken) {
    public AuthResponse(String accessToken, Instant expiresAt, AuthUser user) {
        this(accessToken, expiresAt, user, null);
    }
}
