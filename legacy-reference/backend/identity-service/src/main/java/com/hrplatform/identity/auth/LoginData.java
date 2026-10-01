package com.hrplatform.identity.auth;

import java.time.Instant;

public record LoginData(String accessToken, Instant expiresAt) {
}
