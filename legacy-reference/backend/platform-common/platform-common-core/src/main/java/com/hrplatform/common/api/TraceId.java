package com.hrplatform.common.api;

import java.util.UUID;

public final class TraceId {

    public static final String REQUEST_ATTRIBUTE = TraceId.class.getName() + ".value";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceId() {
    }

    public static String current() {
        String traceId = CURRENT.get();
        return traceId == null ? generate() : traceId;
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isEmpty() || candidate.length() > 64) {
            return null;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c < 0x21 || c > 0x7e) {
                return null;
            }
        }
        return candidate;
    }

    public static void bind(String traceId) {
        CURRENT.set(traceId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
