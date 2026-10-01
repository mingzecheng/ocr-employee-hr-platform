package com.hrplatform.workflow.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.workflow.security.WorkflowActor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestControllerTest {
    private final RequestService service = mock(RequestService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RequestController(service))
            .setControllerAdvice(new WorkflowExceptionHandler())
            .build();
    private final Authentication authentication = new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(7L, "admin", java.util.List.of("SYSTEM_ADMIN"), java.util.List.of(), null), "token");

    @Test
    void createsRequestWithCommonResponseEnvelope() throws Exception {
        RequestDtos.RequestData data = new RequestDtos.RequestData(
                11L, "HR-20260917-ABCD1234", "ONBOARDING", 42L, 7L,
                new ObjectMapper().createObjectNode(), "DRAFT", "DRAFT");
        when(service.create(any(), any(WorkflowActor.class))).thenReturn(data);

        mockMvc.perform(post("/api/hr-requests")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestType\":\"ONBOARDING\",\"employeeId\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.requestNo").value("HR-20260917-ABCD1234"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }
}
