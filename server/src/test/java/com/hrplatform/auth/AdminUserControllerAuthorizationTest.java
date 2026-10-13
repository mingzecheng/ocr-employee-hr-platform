package com.hrplatform.auth;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private AdminUserService service;

    @Test
    void systemAdministratorGetsMinimalAccountRowsWithoutPasswordHash() throws Exception {
        when(service.list(1, 20)).thenReturn(new AdminUserPage(List.of(
                new AdminUserView(10L, "demo-admin", null, null, true, Set.of("SYSTEM_ADMIN"))), 1, 1, 20));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer("SYSTEM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].username").value("demo-admin"))
                .andExpect(jsonPath("$.data.items[0].roleCodes[0]").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
    }

    @Test
    void hrAdministratorCannotManageAccounts() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", bearer("HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"staff","password":"password123","employeeId":20,
                                 "departmentId":3,"enabled":true,"roleCodes":["EMPLOYEE"]}
                                """))
                .andExpect(status().isForbidden());
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(new JwtPrincipal(7L, null, null, Set.of(role)));
    }
}
