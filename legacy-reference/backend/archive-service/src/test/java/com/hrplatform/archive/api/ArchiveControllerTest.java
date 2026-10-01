package com.hrplatform.archive.api;

import com.hrplatform.archive.document.ArchiveService;
import com.hrplatform.archive.employee.EmployeeService;
import com.hrplatform.archive.ocr.OcrPreviewMissingException;
import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArchiveControllerTest {
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final ArchiveService archiveService = mock(ArchiveService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ArchiveController(employeeService, archiveService))
            .setControllerAdvice(new ArchiveExceptionHandler())
            .build();

    @Test
    void createsEmployeeUsingTheCommonResponseEnvelope() throws Exception {
        when(employeeService.create(any(ArchiveDtos.EmployeeCreateRequest.class), any(DataScope.class)))
                .thenReturn(new ArchiveDtos.EmployeeData(7L, "E-001", "张三", null, null, "ACTIVE"));

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNo\":\"E-001\",\"name\":\"张三\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    void listsEmployeesWithPaginationMetadata() throws Exception {
        when(employeeService.list(eq(2), eq(10), eq("张"), eq("ACTIVE"), any(DataScope.class)))
                .thenReturn(new ArchiveDtos.EmployeePageData(
                        java.util.List.of(new ArchiveDtos.EmployeeData(7L, "E-001", "张三", null, null, "ACTIVE")),
                        21L, 2, 10));

        mockMvc.perform(get("/api/employees?page=2&pageSize=10&keyword=张&status=ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(10));
    }

    @Test
    void uploadsImageAndReturnsTheCreatedVersion() throws Exception {
        when(archiveService.uploadDocument(eq(7L), eq("employee_profile"), any(), any(), eq(null),
                any(DataScope.class)))
                .thenReturn(new ArchiveDtos.DocumentUploadData(
                        7L, 11L, 21L, "archive/7/object.png", "abc", 3, "DRAFT"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/archive/employees/7/documents")
                        .file(file)
                        .param("documentType", "employee_profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(21))
                .andExpect(jsonPath("$.data.sha256").value("abc"));
    }

    @Test
    void proxiesDetectionPreviewAndMapsMissingPreview() throws Exception {
        when(archiveService.detectionPreview(eq(31L), any(DataScope.class))).thenReturn(new byte[]{8, 9});

        mockMvc.perform(get("/api/archive/ocr-bindings/31/detection-preview"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[]{8, 9}));

        when(archiveService.detectionPreview(eq(32L), any(DataScope.class)))
                .thenThrow(new OcrPreviewMissingException("task-32"));
        mockMvc.perform(get("/api/archive/ocr-bindings/32/detection-preview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DETECTION_PREVIEW_NOT_FOUND"));
    }

    @Test
    void listsDocumentsAndVersionsThroughTheArchiveRoutes() throws Exception {
        ArchiveDtos.DocumentData document = new ArchiveDtos.DocumentData(
                21L, 7L, "EMPLOYEE_PROFILE", "profile.png", "ACTIVE", 31L, 1,
                "profile.png", "image/png", 3L, null);
        ArchiveDtos.VersionData version = new ArchiveDtos.VersionData(
                31L, 21L, 1, "DRAFT", "profile.png", "image/png", 3L, "abc", 9L, null);
        when(archiveService.listDocuments(eq(7L), any(DataScope.class))).thenReturn(java.util.List.of(document));
        when(archiveService.listVersions(eq(21L), any(DataScope.class))).thenReturn(java.util.List.of(version));

        mockMvc.perform(get("/api/archive/employees/7/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(21));
        mockMvc.perform(get("/api/archive/documents/21/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(31));
    }

    @Test
    void downloadsVersionWithStoredFileMetadata() throws Exception {
        when(archiveService.downloadVersion(eq(31L), any(DataScope.class)))
                .thenReturn(new ArchiveDtos.DownloadData("profile.png", "image/png", new byte[]{1, 2, 3}));

        mockMvc.perform(get("/api/archive/versions/31/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("profile.png")));
    }
}
