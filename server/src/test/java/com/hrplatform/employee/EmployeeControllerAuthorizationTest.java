package com.hrplatform.employee;

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

import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeControllerAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void hrAdministratorCanUpdateEmployeeBasicProfile() throws Exception {
        when(employeeService.updateProfile(7L, new EmployeeProfileUpdateRequest("新姓名", "13800138001"), 99L))
                .thenReturn(new Employee(7L, "EMP-007", "新姓名", "13800138001", 3L, 4L,
                        EmployeeStatus.ACTIVE, null, null));

        mockMvc.perform(put("/api/employees/7/profile")
                        .header("Authorization", bearer("HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新姓名\",\"phone\":\"13800138001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新姓名"))
                .andExpect(jsonPath("$.data.phone").value("13800138001"));
    }

    @Test
    void employeeCannotUpdateEmployeeBasicProfile() throws Exception {
        mockMvc.perform(put("/api/employees/7/profile")
                        .header("Authorization", bearer("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新姓名\",\"phone\":\"13800138001\"}"))
                .andExpect(status().isForbidden());

        verify(employeeService, never()).updateProfile(
                7L, new EmployeeProfileUpdateRequest("新姓名", "13800138001"), 99L);
    }

    @Test
    void profileUpdateRejectsBlankName() throws Exception {
        mockMvc.perform(put("/api/employees/7/profile")
                        .header("Authorization", bearer("HR_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"phone\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(new JwtPrincipal(99L, 20L, 3L, Set.of(role)));
    }
}
