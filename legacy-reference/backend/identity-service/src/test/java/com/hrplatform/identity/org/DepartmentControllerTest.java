package com.hrplatform.identity.org;

import com.hrplatform.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DepartmentControllerTest {

    @Test
    void returnsDepartmentTreeInCommonEnvelope() throws Exception {
        DepartmentService service = mock(DepartmentService.class);
        DepartmentNode root = new DepartmentNode();
        root.setCode("HQ");
        root.setName("总部");
        root.setActive(true);
        when(service.findActiveTree()).thenReturn(List.of(root));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DepartmentController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/departments/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].code").value("HQ"));
    }
}
