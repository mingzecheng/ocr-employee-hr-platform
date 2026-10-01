package com.hrplatform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(String secret, String issuer, Duration accessTokenTtl) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
    }

    public IssuedToken issue(long userId,
                             String username,
                             List<String> roles,
                             List<String> permissions,
                             Long employeeId) {
        return issue(userId, username, roles, permissions, employeeId, null);
    }

    public IssuedToken issue(long userId,
                             String username,
                             List<String> roles,
                             List<String> permissions,
                             Long employeeId,
                             Long departmentId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String token = Jwts.builder()
                .subject(Long.toString(userId))
                .issuer(issuer)
                .claim("username", username)
                .claim("roles", roles == null ? List.of() : roles)
                .claim("permissions", permissions == null ? List.of() : permissions)
                .claim("employeeId", employeeId)
                .claim("departmentId", departmentId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString().replace("-", ""))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public JwtPrincipal parse(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        long userId = Long.parseLong(claims.getSubject());
        return new JwtPrincipal(
                userId,
                claims.get("username", String.class),
                stringList(claims.get("roles")),
                stringList(claims.get("permissions")),
                numberValue(claims.get("employeeId")),
                numberValue(claims.get("departmentId")));
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private Long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
