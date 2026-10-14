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
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P-3 审计详情 hasChanges：读操作（LOGIN）before/after 均为 null -> hasChanges=false；
 * 写操作（UPDATE）有快照 -> hasChanges=true。前端据此 v-if 隐藏"变更详情"。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationLogDetailHasChangesTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OperationLogMapper mapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loginLogDetailHasNoChanges() throws Exception {
        mapper.insert(new OperationLogRecord(null, 7L, "LOGIN", "SYS_USER", 7L,
                null, null, "SUCCESS", "trace-hc-login", LocalDateTime.now(),
                "198.51.100.20", "HasChangesAgent/1.0", "POST", "/api/auth/login", null));
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM operation_log WHERE trace_id='trace-hc-login'", Long.class);

        mockMvc.perform(get("/api/operation-logs/" + id).header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("LOGIN"))
                .andExpect(jsonPath("$.data.beforeSummary").doesNotExist())
                .andExpect(jsonPath("$.data.afterSummary").doesNotExist())
                .andExpect(jsonPath("$.data.hasChanges").value(false));
    }

    @Test
    void updateLogDetailHasChanges() throws Exception {
        mapper.insert(new OperationLogRecord(null, 7L, "UPDATE", "EMPLOYEE_ARCHIVE", 3L,
                "部门=旧部门", "部门=新部门", "SUCCESS", "trace-hc-update", LocalDateTime.now(),
                "198.51.100.21", "HasChangesAgent/1.0", "PUT", "/api/employees/3", null));
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM operation_log WHERE trace_id='trace-hc-update'", Long.class);

        mockMvc.perform(get("/api/operation-logs/" + id).header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("UPDATE"))
                .andExpect(jsonPath("$.data.beforeSummary").value("部门=旧部门"))
                .andExpect(jsonPath("$.data.afterSummary").value("部门=新部门"))
                .andExpect(jsonPath("$.data.hasChanges").value(true));
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(new JwtPrincipal(42L, null, null, Set.of(role)));
    }
}
