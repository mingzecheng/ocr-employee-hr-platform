package com.hrplatform.common.web;

import org.slf4j.MDC;

public record ApiResponse<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("0", "OK", data, resolveTraceId(traceId));
    }

    public static <T> ApiResponse<T> failure(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, resolveTraceId(traceId));
    }

    private static String resolveTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank() && !"unknown".equalsIgnoreCase(traceId)) {
            return traceId;
        }
        String requestTraceId = MDC.get(TraceIdFilter.HEADER);
        if (requestTraceId != null && !requestTraceId.isBlank()) {
            return requestTraceId;
        }
        return traceId == null || traceId.isBlank() ? "unknown" : traceId;
    }
}
