package com.hrplatform.common.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String permission(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return "hr:perm:user:" + userId;
    }

    public static String idempotent(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        String normalized = requestId.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("requestId is too long");
        }
        return "hr:idempotent:" + normalized;
    }

    public static String departmentTree() {
        return "hr:org:department:tree";
    }

    public static String todo(long userId, String scopeKey) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        String normalizedScope = scopeKey == null || scopeKey.isBlank() ? "none" : scopeKey.trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedScope.getBytes(StandardCharsets.UTF_8));
            return "hr:todo:user:" + userId + ":" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static String archiveList(String scopeKey, String normalizedQuery) {
        String normalizedScope = scopeKey == null || scopeKey.isBlank() ? "none" : scopeKey.trim();
        String query = normalizedQuery == null ? "" : normalizedQuery.trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((normalizedScope + "|" + query).getBytes(StandardCharsets.UTF_8));
            return archiveListPrefix() + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static String archiveListPrefix() {
        return "hr:list:archive:";
    }
}
