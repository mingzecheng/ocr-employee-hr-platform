package com.hrplatform.organization;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationControllerAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private OrganizationService organizationService;

    @Test
    void hrAdministratorCanReadAllDepartmentsForMaintenance() throws Exception {
        when(organizationService.adminDepartments()).thenReturn(List.of(
                new DepartmentAdminView(1L, "OPS", "运营部", null, null, 1, true)));

        mockMvc.perform(get("/api/departments/admin")
                        .header("Authorization", bearer("HR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("OPS"));
    }

    @Test
    void employeeCannotCreateDepartment() throws Exception {
        mockMvc.perform(post("/api/departments")
                        .header("Authorization", bearer("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"OPS","name":"运营部","parentId":null,"managerEmployeeId":null,
                                 "sortOrder":1,"enabled":true}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrAdminCannotDeleteDepartment() throws Exception {
        mockMvc.perform(delete("/api/departments/1")
                        .header("Authorization", bearer("HR_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemAdminCanDeleteDepartment() throws Exception {
        mockMvc.perform(delete("/api/departments/1")
                        .header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void systemAdminCanDeletePosition() throws Exception {
        mockMvc.perform(delete("/api/departments/1/positions/2")
                        .header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk());
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(new JwtPrincipal(7L, 20L, 1L, Set.of(role)));
    }
}
