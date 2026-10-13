package com.hrplatform.employee;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeStatusHistoryControllerAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void departmentManagerCanReadHistoryWithinTheServiceDataScope() throws Exception {
        when(employeeService.listStatusHistory(eq(7L), eq(1), eq(20), eq(
                new com.hrplatform.common.security.DataScope(
                        com.hrplatform.common.security.DataScope.Type.DEPARTMENT, 45L, null, 10L))))
                .thenReturn(new EmployeeStatusHistoryPage(List.of(
                        new EmployeeStatusHistoryView(601L, 7L, "PENDING_ONBOARD", "ACTIVE",
                                10L, "原部门", 20L, "新部门", 11L, "原岗位", 21L, "新岗位",
                                501L, "HR-501", 99L, "hr-admin", null)), 1, 1, 20));

        mockMvc.perform(get("/api/employees/7/status-history")
                        .header("Authorization", bearer("DEPT_MANAGER", 45L, null, 10L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].beforeDepartmentName").value("原部门"))
                .andExpect(jsonPath("$.data.items[0].afterDepartmentName").value("新部门"))
                .andExpect(jsonPath("$.data.items[0].requestNo").value("HR-501"));
    }

    @Test
    void unauthenticatedHistoryRequestIsRejectedBeforeServiceAccess() throws Exception {
        mockMvc.perform(get("/api/employees/7/status-history"))
                .andExpect(status().isForbidden());

        verify(employeeService, never()).listStatusHistory(eq(7L), eq(1), eq(20), org.mockito.ArgumentMatchers.any());
    }

    private String bearer(String role, Long userId, Long employeeId, Long departmentId) {
        return "Bearer " + jwtService.issue(new JwtPrincipal(userId, employeeId, departmentId, Set.of(role)));
    }
}
