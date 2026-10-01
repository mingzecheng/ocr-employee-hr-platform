package com.hrplatform.archive.api;

import com.hrplatform.archive.document.ArchiveService;
import com.hrplatform.archive.employee.EmployeeService;
import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.api.TraceId;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.common.security.JwtPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
public class ArchiveController {
    private final EmployeeService employeeService;
    private final ArchiveService archiveService;

    public ArchiveController(EmployeeService employeeService, ArchiveService archiveService) {
        this.employeeService = employeeService;
        this.archiveService = archiveService;
    }

    @PostMapping("/api/employees")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_WRITE')")
    public ApiResponse<ArchiveDtos.EmployeeData> createEmployee(
            @Valid @RequestBody ArchiveDtos.EmployeeCreateRequest request,
            Authentication authentication) {
        return ApiResponse.success(employeeService.create(request, scope(authentication)), TraceId.current());
    }

    @GetMapping("/api/employees")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_READ')")
    public ApiResponse<ArchiveDtos.EmployeePageData> listEmployees(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ALL") String status,
            Authentication authentication) {
        return ApiResponse.success(employeeService.list(page, pageSize, keyword, status, scope(authentication)), TraceId.current());
    }

    @PostMapping(value = "/api/archive/employees/{employeeId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_WRITE')")
    public ApiResponse<ArchiveDtos.DocumentUploadData> uploadDocument(
            @PathVariable long employeeId,
            @RequestParam(defaultValue = "employee_profile") String documentType,
            @RequestParam(required = false) String title,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ApiResponse.success(archiveService.uploadDocument(employeeId, documentType, title, file,
                operatorId(authentication), scope(authentication)), TraceId.current());
    }

    @GetMapping("/api/archive/employees/{employeeId}/documents")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_READ')")
    public ApiResponse<List<ArchiveDtos.DocumentData>> listDocuments(
            @PathVariable long employeeId, Authentication authentication) {
        return ApiResponse.success(archiveService.listDocuments(employeeId, scope(authentication)),
                TraceId.current());
    }

    @GetMapping("/api/archive/documents/{documentId}/versions")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_READ')")
    public ApiResponse<List<ArchiveDtos.VersionData>> listVersions(
            @PathVariable long documentId, Authentication authentication) {
        return ApiResponse.success(archiveService.listVersions(documentId, scope(authentication)),
                TraceId.current());
    }

    @GetMapping("/api/archive/versions/{versionId}/download")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_READ')")
    public ResponseEntity<byte[]> downloadVersion(@PathVariable long versionId,
                                                   Authentication authentication) {
        ArchiveDtos.DownloadData file = archiveService.downloadVersion(versionId, scope(authentication));
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.contentType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(file.content().length)
                .header("Content-Disposition", ContentDisposition.inline()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @PostMapping("/api/archive/versions/{versionId}/ocr")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_WRITE')")
    public ApiResponse<ArchiveDtos.OcrResultData> runOcr(
            @PathVariable long versionId,
            @RequestParam(defaultValue = "employee_profile") String documentType,
            Authentication authentication) {
        return ApiResponse.success(archiveService.runOcr(versionId, documentType, operatorId(authentication),
                        scope(authentication)),
                TraceId.current());
    }

    @GetMapping("/api/archive/versions/{versionId}/ocr-result")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_READ')")
    public ApiResponse<ArchiveDtos.OcrResultData> getOcrResult(@PathVariable long versionId,
                                                               Authentication authentication) {
        return ApiResponse.success(archiveService.getOcrResult(versionId, scope(authentication)), TraceId.current());
    }

    @GetMapping("/api/archive/ocr-bindings/{bindingId}/detection-preview")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_READ')")
    public ResponseEntity<byte[]> getDetectionPreview(@PathVariable long bindingId,
                                                       Authentication authentication) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(archiveService.detectionPreview(bindingId, scope(authentication)));
    }

    @PutMapping("/api/archive/ocr-bindings/{bindingId}/fields/{fieldCode}")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_WRITE')")
    public ApiResponse<ArchiveDtos.OcrResultData> correctField(
            @PathVariable long bindingId,
            @PathVariable String fieldCode,
            @Valid @RequestBody ArchiveDtos.FieldCorrectionRequest request,
            Authentication authentication) {
        return ApiResponse.success(archiveService.correctField(bindingId, fieldCode, request.value(),
                request.reason(), operatorId(authentication), scope(authentication)), TraceId.current());
    }

    private Long operatorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private DataScope scope(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return DataScope.fromPrincipal(principal);
        }
        return DataScope.none(0L);
    }
}
