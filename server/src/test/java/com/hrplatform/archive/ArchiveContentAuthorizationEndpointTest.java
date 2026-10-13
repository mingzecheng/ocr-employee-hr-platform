package com.hrplatform.archive;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.security.JwtService;
import com.hrplatform.ocr.OcrBinding;
import com.hrplatform.ocr.OcrBindingMapper;
import com.hrplatform.ocr.OcrService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.archive.ArchiveReadAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArchiveContentAuthorizationEndpointTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private ArchiveMapper archiveMapper;
    @MockitoBean
    private ObjectStorage objectStorage;
    @MockitoBean
    private ArchiveReadAuthorization readAuthorization;
    @MockitoBean
    private OcrService ocrService;
    @MockitoBean
    private OcrBindingMapper bindingMapper;

    @Test
    void archiveDownloadRequiresAnActiveContentGrant() throws Exception {
        DataScope scope = new DataScope(DataScope.Type.EMPLOYEE, 45L, 7L, 10L);
        when(archiveMapper.findVersionByIdWithScope(20L, 7L, 10L, "EMPLOYEE"))
                .thenReturn(version(20L, 12L));
        when(readAuthorization.allowsRead(20L, 12L, scope)).thenReturn(false);

        mockMvc.perform(get("/api/archive/versions/20/download")
                        .header("Authorization", bearer("EMPLOYEE", 45L, 7L, 10L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(objectStorage, never()).get(any());
    }

    @Test
    void ocrResultIsHiddenWhenTheEmployeeHasNoActiveContentGrant() throws Exception {
        givenReadableSourceForEmployee();

        mockMvc.perform(get("/api/archive/versions/20/ocr-result")
                        .header("Authorization", bearer("EMPLOYEE", 45L, 7L, 10L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(ocrService, never()).getResult(eq(20L), any());
    }

    @Test
    void ocrPreviewIsHiddenWhenTheEmployeeHasNoActiveContentGrant() throws Exception {
        givenReadableSourceForEmployee();
        when(bindingMapper.findByIdWithScope(30L, 7L, 10L, "EMPLOYEE"))
                .thenReturn(binding());

        mockMvc.perform(get("/api/archive/ocr-bindings/30/preview")
                        .header("Authorization", bearer("EMPLOYEE", 45L, 7L, 10L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(ocrService, never()).preview(30L, new DataScope(DataScope.Type.EMPLOYEE, 45L, 7L, 10L));
    }

    @Test
    void ocrTriggerRequiresAnActiveContentGrantEvenForHr() throws Exception {
        DataScope scope = DataScope.fromClaims(45L, null, null, Set.of("HR_ADMIN"));
        when(archiveMapper.findOcrSourceByVersionIdWithScope(20L, null, null, "ALL"))
                .thenReturn(source());
        when(readAuthorization.allowsRead(20L, 12L, scope)).thenReturn(false);

        mockMvc.perform(post("/api/archive/versions/20/ocr")
                        .header("Authorization", bearer("HR_ADMIN", 45L, null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(ocrService, never()).triggerResult(any());
    }

    @Test
    void ocrFieldRevisionRequiresAnActiveContentGrantEvenForHr() throws Exception {
        DataScope scope = DataScope.fromClaims(45L, null, null, Set.of("HR_ADMIN"));
        when(bindingMapper.findByIdWithScope(30L, null, null, "ALL")).thenReturn(binding());
        when(archiveMapper.findOcrSourceByVersionIdWithScope(20L, null, null, "ALL"))
                .thenReturn(source());
        when(readAuthorization.allowsRead(20L, 12L, scope)).thenReturn(false);

        mockMvc.perform(put("/api/archive/ocr-bindings/30/fields/name")
                        .header("Authorization", bearer("HR_ADMIN", 45L, null, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"林晓\",\"reason\":\"核对原件\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(ocrService, never()).reviseField(eq(30L), eq("name"), eq("林晓"), eq("核对原件"),
                eq(45L), any());
    }

    private void givenReadableSourceForEmployee() {
        DataScope scope = new DataScope(DataScope.Type.EMPLOYEE, 45L, 7L, 10L);
        when(archiveMapper.findOcrSourceByVersionIdWithScope(20L, 7L, 10L, "EMPLOYEE"))
                .thenReturn(source());
        when(readAuthorization.allowsRead(20L, 12L, scope)).thenReturn(false);
    }

    private ArchiveVersion version(Long id, Long createdBy) {
        return new ArchiveVersion(id, 9L, 1, 30L, "profile.png", "image/png", 68L, "hash",
                "UPLOADED", true, LocalDateTime.now(), createdBy, "initial");
    }

    private ArchiveOcrSource source() {
        return new ArchiveOcrSource(20L, "archive/7/profile.png", "profile.png", "image/png",
                68L, "hash", "employee_profile", 7L, 10L, 12L);
    }

    private OcrBinding binding() {
        return new OcrBinding(30L, 20L, "task-20", "SUCCEEDED", "PaddleOCR", 40L,
                false, null, LocalDateTime.now(), "employee_profile");
    }

    private String bearer(String role, Long userId, Long employeeId, Long departmentId) {
        return "Bearer " + jwtService.issue(new JwtPrincipal(userId, employeeId, departmentId, Set.of(role)));
    }
}
