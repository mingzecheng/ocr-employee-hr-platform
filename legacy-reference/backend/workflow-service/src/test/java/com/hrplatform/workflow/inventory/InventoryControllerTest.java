package com.hrplatform.workflow.inventory;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.workflow.security.WorkflowActor;
import com.hrplatform.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryControllerTest {
    private final InventoryService service = mock(InventoryService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new InventoryController(service))
            .setControllerAdvice(new InventoryExceptionHandler(), new GlobalExceptionHandler())
            .build();
    private final Authentication authentication = new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(7L, "operator", List.of("HR_ADMIN"), List.of("INVENTORY_WRITE"), 42L), "token");

    @Test
    void createsAndStartsInventoryWithCommonEnvelope() throws Exception {
        when(service.create(any(), any(WorkflowActor.class))).thenReturn(taskData("DRAFT"));
        when(service.start(eq(101L), any(WorkflowActor.class))).thenReturn(taskData("IN_PROGRESS"));

        mockMvc.perform(post("/api/inventory-tasks")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scopeType":"EMPLOYEE","scopeValue":"42",
                                 "items":[{"archiveDocumentId":501,"expectedVersionId":601}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mockMvc.perform(post("/api/inventory-tasks/101/start").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void returnsStableStateErrorAndTaskDetails() throws Exception {
        when(service.get(eq(101L), any(WorkflowActor.class))).thenReturn(taskData("ABNORMAL"));
        when(service.complete(eq(101L), any(WorkflowActor.class)))
                .thenThrow(new InventoryStateException("IN_PROGRESS", "DRAFT"));

        mockMvc.perform(get("/api/inventory-tasks/101").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ABNORMAL"));
        mockMvc.perform(post("/api/inventory-tasks/101/complete").principal(authentication))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_NOT_ALLOWED"));
    }

    @Test
    void validatesRequiredInventoryScope() throws Exception {
        mockMvc.perform(post("/api/inventory-tasks")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeType\":\"EMPLOYEE\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private InventoryDtos.TaskData taskData(String status) {
        return new InventoryDtos.TaskData(101L, "INV-TEST", "EMPLOYEE", "42", 7L,
                status, null, null, List.of(), List.of());
    }
}
