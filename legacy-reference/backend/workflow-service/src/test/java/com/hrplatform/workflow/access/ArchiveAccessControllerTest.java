package com.hrplatform.workflow.access;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.workflow.security.WorkflowActor;
import com.hrplatform.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArchiveAccessControllerTest {
    private final ArchiveAccessService service = mock(ArchiveAccessService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ArchiveAccessController(service))
            .setControllerAdvice(new ArchiveAccessExceptionHandler(), new GlobalExceptionHandler())
            .build();
    private final Authentication authentication = new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(7L, "operator", List.of("HR_OPERATOR"), List.of(), 42L), "token");

    @Test
    void createsApplicationThroughCommonResponseEnvelope() throws Exception {
        when(service.create(any(), any(WorkflowActor.class))).thenReturn(accessData("DRAFT"));

        mockMvc.perform(post("/api/archive-access-applications")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":42,"useType":"PAPER_BORROW","purpose":"入职核验",
                                 "startAt":"2030-01-01T09:00:00","dueAt":"2030-01-03T18:00:00",
                                 "returnRequired":true,"items":[{"archiveDocumentId":501,"archiveVersionId":601,"scope":"FULL"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void approvesApplicationWithOperatorAndComment() throws Exception {
        when(service.approve(eq(101L), any(WorkflowActor.class), eq(true), eq("同意")))
                .thenReturn(accessData("PENDING_HR_APPROVAL"));

        mockMvc.perform(post("/api/archive-access-applications/101/approve")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_HR_APPROVAL"));
    }

    @Test
    void returnsUseAndReportsStateConflictWithStableCode() throws Exception {
        when(service.returnUse(eq(202L), any(WorkflowActor.class), any()))
                .thenThrow(new ArchiveAccessStateException("IN_USE", "RETURNED"));

        mockMvc.perform(post("/api/archive-uses/202/return")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"returnType\":\"NORMAL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_NOT_ALLOWED"));
    }

    @Test
    void exposesApplicationDetailsAndCheckoutUse() throws Exception {
        when(service.get(eq(101L), any(WorkflowActor.class))).thenReturn(accessData("APPROVED"));
        when(service.checkout(eq(101L), any(WorkflowActor.class))).thenReturn(useData("IN_USE"));

        mockMvc.perform(get("/api/archive-access-applications/101").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(101));
        mockMvc.perform(post("/api/archive-access-applications/101/checkout").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_USE"));
    }

    @Test
    void rejectsApplicationWithoutMaterialScopeWithCommonValidationError() throws Exception {
        mockMvc.perform(post("/api/archive-access-applications")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":42,"useType":"PAPER_BORROW","purpose":"入职核验",
                                 "startAt":"2030-01-01T09:00:00","dueAt":"2030-01-03T18:00:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private ArchiveAccessDtos.AccessData accessData(String status) {
        return new ArchiveAccessDtos.AccessData(101L, "AA-20300101-TEST", 7L, 42L,
                "PAPER_BORROW", "入职核验", LocalDateTime.parse("2030-01-01T09:00:00"),
                LocalDateTime.parse("2030-01-03T18:00:00"), true, status, "DRAFT", List.of(), List.of(), null);
    }

    private ArchiveAccessDtos.UseData useData(String status) {
        return new ArchiveAccessDtos.UseData(202L, 101L, 7L, LocalDateTime.now(),
                LocalDateTime.now().plusDays(1), null, null, null, null, null, status);
    }
}
