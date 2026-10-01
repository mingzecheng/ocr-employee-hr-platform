package com.hrplatform.archive.api;

import com.hrplatform.archive.document.ArchiveService;
import com.hrplatform.archive.employee.EmployeeService;
import com.hrplatform.archive.ocr.OcrBindingMapper;
import com.hrplatform.archive.ocr.OcrFailureData;
import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.api.TraceId;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.common.security.JwtPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ArchiveAuthorizationController {
    private final EmployeeService employeeService;
    private final ArchiveService archiveService;
    private final OcrBindingMapper bindingMapper;

    public ArchiveAuthorizationController(EmployeeService employeeService, ArchiveService archiveService,
                                          OcrBindingMapper bindingMapper) {
        this.employeeService = employeeService;
        this.archiveService = archiveService;
        this.bindingMapper = bindingMapper;
    }

    @GetMapping("/internal/archive/employees/{employeeId}/authorization")
    public ApiResponse<ArchiveDtos.ResourceAuthorizationData> authorizeEmployee(
            @PathVariable long employeeId, Authentication authentication) {
        return ApiResponse.success(employeeService.authorize(employeeId, scope(authentication)), TraceId.current());
    }

    @GetMapping("/internal/archive/versions/{versionId}/authorization")
    public ApiResponse<ArchiveDtos.ResourceAuthorizationData> authorizeVersion(
            @PathVariable long versionId, Authentication authentication) {
        return ApiResponse.success(archiveService.authorizeVersion(versionId, scope(authentication)), TraceId.current());
    }

    @GetMapping("/internal/archive/documents/{documentId}/authorization")
    public ApiResponse<ArchiveDtos.ResourceAuthorizationData> authorizeDocument(
            @PathVariable long documentId, Authentication authentication) {
        return ApiResponse.success(archiveService.authorizeDocument(documentId, scope(authentication)), TraceId.current());
    }

    @GetMapping("/internal/archive/ocr-failures")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_READ')")
    public ApiResponse<List<OcrFailureData>> listOcrFailures(
            @RequestParam(defaultValue = "20") int limit, Authentication authentication) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        int boundedLimit = Math.min(limit, 100);
        DataScope dataScope = scope(authentication);
        return ApiResponse.success(bindingMapper.listFailuresInScope(boundedLimit, dataScope.type().name(),
                dataScope.employeeId(), dataScope.departmentId()), TraceId.current());
    }

    private DataScope scope(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return DataScope.fromPrincipal(principal);
        }
        return DataScope.none(0L);
    }
}
