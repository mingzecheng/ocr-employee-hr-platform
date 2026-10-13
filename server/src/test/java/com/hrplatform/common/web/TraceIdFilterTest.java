package com.hrplatform.common.web;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {
    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void replacesUnknownPlaceholderAndSharesTheGeneratedIdWithTheRequestContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.HEADER, "unknown");
        AtomicReference<String> requestContextTraceId = new AtomicReference<>();

        filter.doFilter(request, response, (ServletRequest ignoredRequest, ServletResponse ignoredResponse) ->
                requestContextTraceId.set(MDC.get(TraceIdFilter.HEADER)));

        String responseTraceId = response.getHeader(TraceIdFilter.HEADER);
        assertThat(responseTraceId).isNotEqualTo("unknown");
        assertThat(UUID.fromString(responseTraceId)).isNotNull();
        assertThat(requestContextTraceId.get()).isEqualTo(responseTraceId);
        assertThat(MDC.get(TraceIdFilter.HEADER)).isNull();
    }

    @Test
    void replacesUnsafeClientProvidedTraceIds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.HEADER, "trace with spaces");

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(UUID.fromString(response.getHeader(TraceIdFilter.HEADER))).isNotNull();
    }
}
