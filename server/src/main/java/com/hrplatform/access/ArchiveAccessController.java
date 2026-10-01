package com.hrplatform.access;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/archive-access")
public class ArchiveAccessController {
    private final ArchiveAccessService service;

    public ArchiveAccessController(ArchiveAccessService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<ArchiveAccessApplication> create(@Valid @RequestBody AccessCreateRequest request,
                                                        Authentication authentication) {
        return ApiResponse.success(service.create(request, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ArchiveAccessApplication> submit(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.submit(id, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ArchiveAccessApplication> approve(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.approve(id, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/use")
    public ApiResponse<ArchiveAccessApplication> use(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.use(id, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/return")
    public ApiResponse<ArchiveAccessApplication> returnAccess(@PathVariable Long id,
                                                               @Valid @RequestBody ReturnRequest request,
                                                               Authentication authentication) {
        return ApiResponse.success(service.returnAccess(id, actor(authentication), request.abnormal(), request.remark()),
                "unknown");
    }

    @GetMapping
    public ApiResponse<AccessPage> list(@org.springframework.web.bind.annotation.RequestParam(required = false) String status,
                                        @org.springframework.web.bind.annotation.RequestParam(required = false) Long applicantId,
                                        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
                                        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int pageSize,
                                        Authentication authentication) {
        return ApiResponse.success(service.list(status, applicantId, page, pageSize, actor(authentication)), "unknown");
    }

    private AccessActor actor(Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return new AccessActor(principal.userId(), principal.roles(), principal.dataScope());
    }

    public record ReturnRequest(boolean abnormal, @Size(max = 512) String remark) {
    }
}
