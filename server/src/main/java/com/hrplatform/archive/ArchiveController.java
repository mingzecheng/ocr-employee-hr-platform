package com.hrplatform.archive;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/archive")
public class ArchiveController {
    private final ArchiveService service;

    public ArchiveController(ArchiveService service) {
        this.service = service;
    }

    @PostMapping(value = "/employees/{employeeId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ArchiveVersion> upload(
            @PathVariable Long employeeId,
            @RequestPart String documentType,
            @RequestPart MultipartFile file,
            Authentication authentication
    ) throws IOException {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        ArchiveVersion version = service.createVersion(employeeId, documentType, file.getOriginalFilename(),
                file.getContentType(), file.getBytes(), principal.dataScope(), principal.userId());
        return ApiResponse.success(version, "unknown");
    }

    @GetMapping("/employees/{employeeId}/documents")
    public ApiResponse<ArchiveDocumentPage> documents(@PathVariable Long employeeId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.listDocuments(employeeId, page, pageSize, principal.dataScope()), "unknown");
    }

    @GetMapping("/documents/{documentId}/versions")
    public ApiResponse<ArchiveVersionPage> versions(@PathVariable Long documentId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                    Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.listVersions(documentId, page, pageSize, principal.dataScope()), "unknown");
    }

    @GetMapping("/versions/{versionId}/download")
    public void download(@PathVariable Long versionId, Authentication authentication, HttpServletResponse response) throws IOException {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        ArchiveDownload download = service.download(versionId, principal.dataScope());
        response.setContentType(download.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(download.originalName()).build().toString());
        response.getOutputStream().write(download.content());
    }
}
