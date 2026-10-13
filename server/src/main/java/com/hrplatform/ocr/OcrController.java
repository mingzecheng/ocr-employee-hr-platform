package com.hrplatform.ocr;

import com.hrplatform.archive.ArchiveCatalog;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.archive.ArchiveReadAuthorization;
import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/archive")
public class OcrController {
    private final OcrService ocrService;
    private final ArchiveCatalog archiveCatalog;
    private final OcrBindingMapper bindingMapper;
    private final ArchiveReadAuthorization readAuthorization;
    private final OcrConfirmService confirmService;

    public OcrController(OcrService ocrService, ArchiveCatalog archiveCatalog, OcrBindingMapper bindingMapper,
                         ArchiveReadAuthorization readAuthorization) {
        this(ocrService, archiveCatalog, bindingMapper, readAuthorization, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OcrController(OcrService ocrService, ArchiveCatalog archiveCatalog, OcrBindingMapper bindingMapper,
                         ArchiveReadAuthorization readAuthorization, OcrConfirmService confirmService) {
        this.ocrService = ocrService;
        this.archiveCatalog = archiveCatalog;
        this.bindingMapper = bindingMapper;
        this.readAuthorization = readAuthorization;
        this.confirmService = confirmService;
    }

    @PostMapping("/versions/{versionId}/ocr")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ApiResponse<OcrResult> trigger(@PathVariable Long versionId,
                                          Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        ArchiveOcrSource source = requireReadableSource(versionId, principal);
        OcrResult result = ocrService.triggerResult(new OcrTrigger(source.versionId(), source.objectKey(),
                source.originalName(), source.contentType(), source.size(), source.sha256(), source.documentType(),
                principal.userId()));
        return ApiResponse.success(withSafePreview(result), "unknown");
    }

    @GetMapping("/versions/{versionId}/ocr-result")
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<OcrResult> result(@PathVariable Long versionId, Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        requireReadableSource(versionId, principal);
        return ApiResponse.success(withSafePreview(ocrService.getResult(versionId, principal.dataScope())), "unknown");
    }

    @GetMapping({"/ocr-bindings/{bindingId}/preview", "/ocr-bindings/{bindingId}/detection-preview"})
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ResponseEntity<byte[]> preview(@PathVariable Long bindingId, Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        OcrBinding binding = bindingMapper.findByIdWithScope(bindingId, principal.employeeId(),
                principal.departmentId(), principal.dataScope().type().name());
        if (binding == null) {
            throw new OcrBindingNotFoundException("OCR 绑定不存在或无权访问");
        }
        requireReadableSource(binding.versionId(), principal);
        byte[] content = ocrService.preview(bindingId, principal.dataScope());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("ocr-preview.png", StandardCharsets.UTF_8).build().toString())
                .body(content);
    }

    @PutMapping("/ocr-bindings/{bindingId}/fields/{fieldCode}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ApiResponse<OcrResult> revise(@PathVariable Long bindingId, @PathVariable String fieldCode,
                                         @RequestBody FieldRevisionRequest request,
                                         Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        OcrBinding binding = bindingMapper.findByIdWithScope(bindingId, principal.employeeId(),
                principal.departmentId(), principal.dataScope().type().name());
        if (binding == null) {
            throw new OcrBindingNotFoundException("OCR 绑定不存在或无权访问");
        }
        requireReadableSource(binding.versionId(), principal);
        return ApiResponse.success(ocrService.reviseField(bindingId, fieldCode, request.value(), request.reason(),
                principal.userId(), principal.dataScope()), "unknown");
    }

    @PostMapping("/ocr-bindings/{bindingId}/confirm")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ApiResponse<OcrConfirmResult> confirm(@PathVariable Long bindingId,
                                                 @RequestBody ConfirmRequest request,
                                                 Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        OcrBinding binding = bindingMapper.findByIdWithScope(bindingId, principal.employeeId(),
                principal.departmentId(), principal.dataScope().type().name());
        if (binding == null) {
            throw new OcrBindingNotFoundException("OCR 绑定不存在或无权访问");
        }
        requireReadableSource(binding.versionId(), principal);
        return ApiResponse.success(confirmService.confirm(bindingId, principal.userId(),
                request == null ? null : request.note(), principal.dataScope()), "unknown");
    }

    @PostMapping("/ocr-bindings/{bindingId}/reject")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ApiResponse<OcrConfirmResult> reject(@PathVariable Long bindingId,
                                                @RequestBody ConfirmRequest request,
                                                Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        OcrBinding binding = bindingMapper.findByIdWithScope(bindingId, principal.employeeId(),
                principal.departmentId(), principal.dataScope().type().name());
        if (binding == null) {
            throw new OcrBindingNotFoundException("OCR 绑定不存在或无权访问");
        }
        requireReadableSource(binding.versionId(), principal);
        return ApiResponse.success(confirmService.reject(bindingId, principal.userId(),
                request == null ? null : request.note(), principal.dataScope()), "unknown");
    }

    private ArchiveOcrSource requireReadableSource(Long versionId, JwtPrincipal principal) {
        ArchiveOcrSource source = archiveCatalog.findOcrSourceByVersionIdWithScope(versionId,
                principal.employeeId(), principal.departmentId(), principal.dataScope().type().name());
        if (source == null || !readAuthorization.allowsRead(versionId, source.createdBy(), principal.dataScope())) {
            throw new OcrBindingNotFoundException("档案版本不存在或无有效访问授权");
        }
        return source;
    }

    private JwtPrincipal principal(Authentication authentication) {
        return (JwtPrincipal) authentication.getPrincipal();
    }

    private OcrResult withSafePreview(OcrResult result) {
        if (result == null || result.bindingId() == null || result.taskId() == null || result.taskId().isBlank()) {
            return result;
        }
        return result.withPreviewUrl("/api/archive/ocr-bindings/" + result.bindingId() + "/preview");
    }

    public record FieldRevisionRequest(@NotBlank @Size(max = 512) String value,
                                       @Size(max = 512) String reason) {
    }

    public record ConfirmRequest(@Size(max = 512) String note) {
    }
}
