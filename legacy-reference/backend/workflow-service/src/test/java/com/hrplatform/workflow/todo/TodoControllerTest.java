package com.hrplatform.workflow.todo;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.workflow.authorization.WorkflowDataScopeDeniedException;
import com.hrplatform.workflow.security.WorkflowActor;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TodoControllerTest {
    private final TodoService service = mock(TodoService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TodoController(service))
            .setControllerAdvice(new TodoExceptionHandler())
            .build();
    private final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(7L, "admin", List.of("SYSTEM_ADMIN"), List.of("PERM_HR_REQUEST_READ"), null), "token");

    @Test
    void returnsCommonEnvelopeWithDefaultLimit() throws Exception {
        when(service.list(any(WorkflowActor.class), eq(50))).thenReturn(data());

        mockMvc.perform(get("/api/todos").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void capsLimitAtOneHundred() throws Exception {
        when(service.list(any(WorkflowActor.class), eq(100))).thenReturn(data());

        mockMvc.perform(get("/api/todos?limit=500").principal(authentication))
                .andExpect(status().isOk());

        verify(service).list(any(WorkflowActor.class), eq(100));
    }

    @Test
    void rejectsNonPositiveLimitWithValidationError() throws Exception {
        mockMvc.perform(get("/api/todos?limit=0").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsArchiveUnavailableToBadGateway() throws Exception {
        when(service.list(any(WorkflowActor.class), eq(50))).thenThrow(new TodoArchiveUnavailableException("down"));

        mockMvc.perform(get("/api/todos").principal(authentication))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TODO_SOURCE_UNAVAILABLE"));
    }

    private TodoDtos.TodoData data() {
        return new TodoDtos.TodoData(List.of(), 0, 0, 0, 0, 0, LocalDateTime.now());
    }
}
