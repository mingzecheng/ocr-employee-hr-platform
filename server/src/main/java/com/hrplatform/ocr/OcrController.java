package com.hrplatform.ocr;

import com.hrplatform.archive.ArchiveMapper;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/archive")
public class OcrController {
    private final OcrService ocrService;
    private final ArchiveMapper archiveMapper;

    public OcrController(OcrService ocrService, ArchiveMapper archiveMapper) {
        this.ocrService = ocrService;
        this.archiveMapper = archiveMapper;
    }

    @PostMapping("/versions/{versionId}/ocr")
    public ApiResponse<OcrResult> trigger(@PathVariable Long versionId,
                                          @RequestParam(defaultValue = "employee_profile") String documentType,
                                          Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        ArchiveOcrSource source = archiveMapper.findOcrSourceByVersionIdWithScope(versionId,
                principal.employeeId(), principal.departmentId(), principal.dataScope().type().name());
        if (source == null) {
            throw new OcrBindingNotFoundException("档案版本不存在或无权访问");
        }
        String effectiveType = documentType == null || documentType.isBlank()
                ? source.documentType() : documentType.trim();
        OcrResult result = ocrService.triggerResult(new OcrTrigger(source.versionId(), source.objectKey(),
                source.originalName(), source.contentType(), source.size(), source.sha256(), effectiveType,
                principal.userId()));
        return ApiResponse.success(result, "unknown");
    }

    @GetMapping("/versions/{versionId}/ocr-result")
    public ApiResponse<OcrResult> result(@PathVariable Long versionId, Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        return ApiResponse.success(ocrService.getResult(versionId, principal.dataScope()), "unknown");
    }

    @GetMapping({"/ocr-bindings/{bindingId}/preview", "/ocr-bindings/{bindingId}/detection-preview"})
    public ResponseEntity<byte[]> preview(@PathVariable Long bindingId, Authentication authentication) {
        byte[] content = ocrService.preview(bindingId, principal(authentication).dataScope());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("ocr-preview.png", StandardCharsets.UTF_8).build().toString())
                .body(content);
    }

    @PutMapping("/ocr-bindings/{bindingId}/fields/{fieldCode}")
    public ApiResponse<OcrResult> revise(@PathVariable Long bindingId, @PathVariable String fieldCode,
                                         @RequestBody FieldRevisionRequest request,
                                         Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        return ApiResponse.success(ocrService.reviseField(bindingId, fieldCode, request.value(), request.reason(),
                principal.userId(), principal.dataScope()), "unknown");
    }

    private JwtPrincipal principal(Authentication authentication) {
        return (JwtPrincipal) authentication.getPrincipal();
    }

    public record FieldRevisionRequest(@NotBlank @Size(max = 512) String value,
                                       @Size(max = 512) String reason) {
    }
}
