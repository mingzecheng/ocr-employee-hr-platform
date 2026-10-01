package com.hrplatform.auth;

import java.time.Instant;

public record AuthResponse(String accessToken, Instant expiresAt, AuthUser user) {
}
