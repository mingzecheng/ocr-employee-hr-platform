package com.hrplatform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class JwtService {
    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(String secret, Duration expiration) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String issue(JwtPrincipal principal) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(principal.userId()))
                .claim("employeeId", principal.employeeId())
                .claim("departmentId", principal.departmentId())
                .claim("roles", principal.roles())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    public Duration expiration() {
        return expiration;
    }

    public JwtPrincipal parse(String token) {
        try {
            Claims claims = parseClaims(token);
            return new JwtPrincipal(
                    Long.valueOf(claims.getSubject()),
                    numberClaim(claims, "employeeId"),
                    numberClaim(claims, "departmentId"),
                    rolesClaim(claims.get("roles"))
            );
        } catch (JwtAuthenticationException rethrow) {
            throw rethrow;
        } catch (Exception exception) {
            throw new JwtAuthenticationException("Invalid JWT token", exception);
        }
    }

    /** 提取 token 的 jti（JWT ID），用于登出黑名单。 */
    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    /** 计算 token 剩余有效期（秒级精度，已过期返回 0）。 */
    public Duration remainingTtl(String token) {
        Claims claims = parseClaims(token);
        long expMs = claims.getExpiration().getTime();
        long nowMs = System.currentTimeMillis();
        long seconds = Math.max(0, (expMs - nowMs) / 1000);
        return Duration.ofSeconds(seconds);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        } catch (Exception exception) {
            throw new JwtAuthenticationException("Invalid JWT token", exception);
        }
    }

    private Long numberClaim(Claims claims, String name) {
        Number number = claims.get(name, Number.class);
        return number == null ? null : number.longValue();
    }

    private Set<String> rolesClaim(Object value) {
        if (!(value instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (Object role : list) {
            if (role != null) {
                roles.add(String.valueOf(role));
            }
        }
        return Set.copyOf(roles);
    }
}
