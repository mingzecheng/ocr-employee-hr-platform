package com.hrplatform.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @Test
    void filterCreatesTraceIdAndReturnsItInResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(200);

        new TraceIdFilter().doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
        assertThat(response.getHeader("X-Trace-Id")).hasSize(32);
    }

    @Test
    void filterPropagatesValidTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader("X-Trace-Id", " request-123 ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceIdFilter().doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("request-123");
    }

    @Test
    void filterReplacesOversizedOrNonAsciiTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader("X-Trace-Id", "é".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceIdFilter().doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader("X-Trace-Id")).hasSize(32);
        assertThat(response.getHeader("X-Trace-Id")).doesNotContain("é");
    }
}
