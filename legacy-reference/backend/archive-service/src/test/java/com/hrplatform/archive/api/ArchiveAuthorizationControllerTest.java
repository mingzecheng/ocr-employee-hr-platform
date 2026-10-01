package com.hrplatform.archive.api;

import com.hrplatform.archive.document.ArchiveService;
import com.hrplatform.archive.employee.EmployeeService;
import com.hrplatform.archive.ocr.OcrBindingMapper;
import com.hrplatform.archive.ocr.OcrFailureData;
import com.hrplatform.common.web.GlobalExceptionHandler;
import com.hrplatform.common.security.JwtPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArchiveAuthorizationControllerTest {
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final ArchiveService archiveService = mock(ArchiveService.class);
    private final OcrBindingMapper bindingMapper = mock(OcrBindingMapper.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new ArchiveAuthorizationController(employeeService, archiveService, bindingMapper))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    private final Authentication authentication = new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(7L, "manager", List.of("DEPT_MANAGER"), List.of("ARCHIVE_READ"), null, 9301L),
            "token");

    @Test
    void returnsOcrFailureSummariesInTheCommonEnvelopeAndPassesJwtScope() throws Exception {
        OcrFailureData failure = new OcrFailureData(11L, 22L, 33L, "task-1", "decode failed",
                LocalDateTime.of(2026, 9, 20, 10, 15));
        when(bindingMapper.listFailuresInScope(37, "DEPARTMENT", null, 9301L))
                .thenReturn(List.of(failure));

        mockMvc.perform(get("/internal/archive/ocr-failures")
                        .principal(authentication)
                        .param("limit", "37")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].bindingId").value(11))
                .andExpect(jsonPath("$.data[0].versionId").value(22))
                .andExpect(jsonPath("$.data[0].employeeId").value(33))
                .andExpect(jsonPath("$.data[0].taskId").value("task-1"))
                .andExpect(jsonPath("$.data[0].errorMessage").value("decode failed"))
                .andExpect(jsonPath("$.data[0].updatedAt").exists());

        verify(bindingMapper).listFailuresInScope(37, "DEPARTMENT", null, 9301L);
    }

    @Test
    void capsRequestedFailureLimitAtOneHundred() throws Exception {
        when(bindingMapper.listFailuresInScope(100, "DEPARTMENT", null, 9301L)).thenReturn(List.of());

        mockMvc.perform(get("/internal/archive/ocr-failures")
                        .principal(authentication)
                        .param("limit", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());

        verify(bindingMapper).listFailuresInScope(100, "DEPARTMENT", null, 9301L);
    }

    @Test
    void rejectsNonPositiveFailureLimitWithValidationError() throws Exception {
        mockMvc.perform(get("/internal/archive/ocr-failures")
                        .principal(authentication)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void endpointRequiresArchiveReadPermission() {
        Method method = java.util.Arrays.stream(ArchiveAuthorizationController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("listOcrFailures"))
                .findFirst().orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("PERM_ARCHIVE_READ");
    }
}
