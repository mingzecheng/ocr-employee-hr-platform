package com.hrplatform.admin;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P-1 角色权限矩阵可编辑：
 * <ul>
 *   <li>启动种子化后，GET /api/admin/roles 返回与静态默认一致的矩阵；</li>
 *   <li>PUT 逐格写 sys_role_permission，GET 反映最新值；</li>
 *   <li>撤销 SYSTEM_ADMIN 的"系统监控"后访问 /api/admin/monitor 返回 403，恢复后 200；</li>
 *   <li>非系统管理员无法编辑矩阵。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RolePermissionMatrixIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rolesMatrixSeededToDefaults() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matrix.SYSTEM_ADMIN['系统监控']").value(true))
                .andExpect(jsonPath("$.data.matrix.EMPLOYEE['系统监控']").value(false))
                .andExpect(jsonPath("$.data.matrix.EMPLOYEE['员工档案']").value(true))
                .andExpect(jsonPath("$.data.matrix.HR_ADMIN['系统监控']").value(false));
    }

    @Test
    void putUpdatesPermissionAndMatrixReflectsIt() throws Exception {
        // HR_ADMIN 默认拥有"员工档案"，先撤销再确认矩阵变化，最后恢复。
        try {
            mockMvc.perform(put("/api/admin/roles/HR_ADMIN/permissions")
                            .header("Authorization", bearer("SYSTEM_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"module\":\"员工档案\",\"allowed\":false}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/admin/roles").header("Authorization", bearer("SYSTEM_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.matrix.HR_ADMIN['员工档案']").value(false));

            // DB 中确无 HR_ADMIN↔员工档案 关联行。
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_role_permission rp " +
                            "JOIN sys_role r ON r.id = rp.role_id " +
                            "JOIN sys_permission p ON p.id = rp.permission_id " +
                            "WHERE r.code = 'HR_ADMIN' AND p.code = '员工档案'",
                    Integer.class);
            org.assertj.core.api.Assertions.assertThat(count).isZero();
        } finally {
            mockMvc.perform(put("/api/admin/roles/HR_ADMIN/permissions")
                    .header("Authorization", bearer("SYSTEM_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"module\":\"员工档案\",\"allowed\":true}"));
        }
    }

    @Test
    void revokingSystemMonitorPermissionBlocksMonitorEndpoint() throws Exception {
        // 前置：系统管理员可访问监控。
        mockMvc.perform(get("/api/admin/monitor").header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk());
        try {
            // 撤销 SYSTEM_ADMIN 的"系统监控"模块权限。
            mockMvc.perform(put("/api/admin/roles/SYSTEM_ADMIN/permissions")
                            .header("Authorization", bearer("SYSTEM_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"module\":\"系统监控\",\"allowed\":false}"))
                    .andExpect(status().isOk());

            // 同一管理员再访问监控 -> 403。
            mockMvc.perform(get("/api/admin/monitor").header("Authorization", bearer("SYSTEM_ADMIN")))
                    .andExpect(status().isForbidden());
        } finally {
            // 恢复，避免污染同上下文的其他测试。
            mockMvc.perform(put("/api/admin/roles/SYSTEM_ADMIN/permissions")
                    .header("Authorization", bearer("SYSTEM_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"module\":\"系统监控\",\"allowed\":true}"));
        }
        mockMvc.perform(get("/api/admin/monitor").header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void editMatrixForbiddenForNonSystemAdmin() throws Exception {
        mockMvc.perform(put("/api/admin/roles/HR_ADMIN/permissions")
                        .header("Authorization", bearer("HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"员工档案\",\"allowed\":false}"))
                .andExpect(status().isForbidden());
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(new JwtPrincipal(42L, null, null, Set.of(role)));
    }
}
