package com.hrplatform.audit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RiskFieldsExtractorTest {

    @Test
    void picksFirstForwardedForEntryWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 198.51.100.4");
        request.setRemoteAddr("127.0.0.1");
        assertThat(RiskFieldsExtractor.resolveClientIp(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToRemoteAddrWithoutForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.5.5.5");
        assertThat(RiskFieldsExtractor.resolveClientIp(request)).isEqualTo("10.5.5.5");
    }

    @Test
    void extractsUserAgentMethodAndUriFromRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/operation-logs");
        request.addHeader("User-Agent", "UnitAgent/3.0");
        request.setRemoteAddr("10.0.0.1");
        RiskFields fields = RiskFieldsExtractor.fromRequest(request);
        assertThat(fields.userAgent()).isEqualTo("UnitAgent/3.0");
        assertThat(fields.requestMethod()).isEqualTo("POST");
        assertThat(fields.requestUri()).isEqualTo("/api/operation-logs");
    }

    @Test
    void masksIpv4AndIpv6() {
        assertThat(AuditController.maskIp("192.168.1.42")).isEqualTo("192.168.1.x");
        assertThat(AuditController.maskIp("2001:0db8:85a3:0000:0000:8a2e:0370:7334")).startsWith("2001:0db8");
        assertThat(AuditController.maskIp(null)).isNull();
        assertThat(AuditController.maskIp("")).isNull();
    }
}
