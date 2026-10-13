package com.hrplatform.audit;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditRiskFieldsIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OperationLogMapper mapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void recordsClientIpAndUserAgentFromRequestOnAuditedFailure() throws Exception {
        // 普通员工访问仅 SYSTEM_ADMIN 的详情端点 -> 403 -> 全局处理器记录 REQUEST_FAILED。
        mockMvc.perform(get("/api/operation-logs/999999")
                        .header("Authorization", bearer("EMPLOYEE"))
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .header("User-Agent", "RiskyTestAgent/1.0"))
                .andExpect(status().isForbidden());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT client_ip, user_agent, request_method, request_uri FROM operation_log ORDER BY id DESC LIMIT 1");
        // X-Forwarded-For 存在时优先取第一个 IP。
        assertThat(row.get("client_ip")).isEqualTo("203.0.113.7");
        assertThat(row.get("user_agent")).isEqualTo("RiskyTestAgent/1.0");
        assertThat(row.get("request_method")).isEqualTo("GET");
        assertThat((String) row.get("request_uri")).contains("/api/operation-logs/999999");
    }

    @Test
    void detailReturnsFullRiskFieldsForSystemAdmin() throws Exception {
        Long id = insertLog("192.168.1.42", "DetailAgent/2.0", "GET", "/api/operation-logs");

        mockMvc.perform(get("/api/operation-logs/" + id).header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientIp").value("192.168.1.42"))
                .andExpect(jsonPath("$.data.userAgent").value("DetailAgent/2.0"))
                .andExpect(jsonPath("$.data.requestMethod").value("GET"))
                .andExpect(jsonPath("$.data.requestUri").value("/api/operation-logs"))
                .andExpect(jsonPath("$.data.sessionId").value("sess-jti-abc"))
                .andExpect(jsonPath("$.data.beforeSummary").value("PENDING"))
                .andExpect(jsonPath("$.data.afterSummary").value("APPROVED"))
                .andExpect(jsonPath("$.data.traceId").value("trace-detail"));
    }

    @Test
    void nonSystemAdminCannotReadDetail() throws Exception {
        Long id = insertLog("10.0.0.5", "SomeAgent/1.0", "GET", "/x");
        // HR_ADMIN 可看列表，但详情仅限 SYSTEM_ADMIN。
        mockMvc.perform(get("/api/operation-logs/" + id).header("Authorization", bearer("HR_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void detailReturns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/operation-logs/424242").header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsMaskedClientIpAndHidesRawFingerprint() throws Exception {
        insertLog("192.168.1.42", "ListAgent/1.0", "GET", "/api/operation-logs");

        mockMvc.perform(get("/api/operation-logs").header("Authorization", bearer("HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].clientIpMasked").value("192.168.1.x"))
                .andExpect(jsonPath("$.data.items[0].clientIp").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].userAgent").doesNotExist());
    }

    private Long insertLog(String ip, String ua, String method, String uri) {
        mapper.insert(new OperationLogRecord(null, 42L, "TEST_ACTION", "HR_REQUEST", 7L,
                "PENDING", "APPROVED", "SUCCESS", "trace-detail", LocalDateTime.now(),
                ip, ua, method, uri, "sess-jti-abc"));
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM operation_log", Long.class);
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(new JwtPrincipal(42L, null, null, Set.of(role)));
    }
}
