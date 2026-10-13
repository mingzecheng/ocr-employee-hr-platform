package com.hrplatform.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
    @AfterEach
    void clearTraceContext() {
        MDC.remove(TraceIdFilter.HEADER);
    }

    @Test
    void replacesLegacyUnknownSuccessTraceIdWithRequestTraceId() {
        MDC.put(TraceIdFilter.HEADER, "trace-request-123");

        ApiResponse<String> response = ApiResponse.success("ok", "unknown");

        assertThat(response.traceId()).isEqualTo("trace-request-123");
    }

    @Test
    void replacesLegacyUnknownFailureTraceIdWithRequestTraceId() {
        MDC.put(TraceIdFilter.HEADER, "trace-error-456");

        ApiResponse<Void> response = ApiResponse.failure("FAILED", "failure", "unknown");

        assertThat(response.traceId()).isEqualTo("trace-error-456");
    }

    @Test
    void preservesAnExplicitTraceIdOutsideARequestContext() {
        ApiResponse<String> response = ApiResponse.success("ok", "trace-explicit");

        assertThat(response.traceId()).isEqualTo("trace-explicit");
    }
}
