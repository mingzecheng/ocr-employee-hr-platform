package com.hrplatform.admin;

import com.hrplatform.audit.OperationLogMapper;
import com.hrplatform.audit.OperationLogRecord;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 4 个管理控制台端点的集成测试：校验角色门控（仅 SYSTEM_ADMIN）与返回结构。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminConsoleIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- O-1 monitor ----
    @Test
    void monitorExposesJvmAndAppForSystemAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/monitor").header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jvm.heapMax").isNumber())
                .andExpect(jsonPath("$.data.jvm.threadCount").isNumber())
                .andExpect(jsonPath("$.data.system.cpuCores").isNumber())
                .andExpect(jsonPath("$.data.app.uptimeMs").isNumber())
                // H2 不支持 SHOW GLOBAL STATUS，测试环境 mysql 段降级为 null。
                .andExpect(jsonPath("$.data.mysql").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void monitorForbiddenForNonSystemAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/monitor").header("Authorization", bearer("HR_ADMIN")))
                .andExpect(status().isForbidden());
    }

    // ---- O-2 online users ----
    @Test
    void onlineUsersReturnedAsArrayForSystemAdmin() throws Exception {
        // 测试环境无可用 Redis：降级为空数组，但接口本身必须 200。
        mockMvc.perform(get("/api/admin/online-users").header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void onlineUsersForbiddenForEmployee() throws Exception {
        mockMvc.perform(get("/api/admin/online-users").header("Authorization", bearer("EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void forceOfflineForbiddenForEmployee() throws Exception {
        mockMvc.perform(delete("/api/admin/online-users/42").header("Authorization", bearer("EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    // ---- O-3 roles matrix ----
    @Test
    void rolesMatrixReturnsReadonlyGrid() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$.data.modules.length()").isNumber())
                .andExpect(jsonPath("$.data.matrix.SYSTEM_ADMIN['系统监控']").value(true))
                .andExpect(jsonPath("$.data.matrix.EMPLOYEE['系统监控']").value(false))
                .andExpect(jsonPath("$.data.matrix.EMPLOYEE['员工档案']").value(true));
    }

    @Test
    void rolesForbiddenForEmployee() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header("Authorization", bearer("EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    // ---- O-4 login logs ----
    @Test
    void loginLogsOnlyReturnAuthActionsAndKeepIpAndUserAgent() throws Exception {
        operationLogMapper.insert(new OperationLogRecord(null, 7L, "LOGIN", "SYS_USER", 7L,
                null, null, "SUCCESS", "trace-login", LocalDateTime.now(),
                "198.51.100.9", "LoginLogAgent/3.0", "POST", "/api/auth/login", null));
        operationLogMapper.insert(new OperationLogRecord(null, 7L, "OCR_TRIGGER", "ARCHIVE_VERSION", 1L,
                null, null, "SUCCESS", "trace-ocr", LocalDateTime.now(),
                "10.0.0.1", "OcrAgent/1.0", "POST", "/api/ocr", null));

        mockMvc.perform(get("/api/admin/login-logs").header("Authorization", bearer("SYSTEM_ADMIN"))
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("LOGIN"))
                .andExpect(jsonPath("$.data.items[0].clientIp").value("198.51.100.9"))
                .andExpect(jsonPath("$.data.items[0].userAgent").value("LoginLogAgent/3.0"))
                // OCR_TRIGGER 不应出现在登录日志中。
                .andExpect(jsonPath("$.data.items[?(@.action == 'OCR_TRIGGER')]").doesNotExist());
    }

    @Test
    void loginLogsForbiddenForHrAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/login-logs").header("Authorization", bearer("HR_ADMIN")))
                .andExpect(status().isForbidden());
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(new JwtPrincipal(42L, null, null, Set.of(role)));
    }
}
