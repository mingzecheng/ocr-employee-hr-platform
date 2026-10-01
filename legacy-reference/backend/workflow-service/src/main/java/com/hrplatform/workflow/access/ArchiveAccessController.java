package com.hrplatform.workflow.access;

import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.api.TraceId;
import com.hrplatform.workflow.security.WorkflowActor;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArchiveAccessController {
    private final ArchiveAccessService service;

    public ArchiveAccessController(ArchiveAccessService service) {
        this.service = service;
    }

    @PostMapping("/api/archive-access-applications")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_ACCESS_CREATE')")
    public ApiResponse<ArchiveAccessDtos.AccessData> create(
            @Valid @RequestBody ArchiveAccessDtos.CreateRequest request,
            Authentication authentication) {
        return ApiResponse.success(service.create(request, actor(authentication)), TraceId.current());
    }

    @PostMapping("/api/archive-access-applications/{id}/submit")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_ACCESS_CREATE')")
    public ApiResponse<ArchiveAccessDtos.AccessData> submit(@PathVariable long id,
                                                             Authentication authentication) {
        return ApiResponse.success(service.submit(id, actor(authentication)), TraceId.current());
    }

    @PostMapping("/api/archive-access-applications/{id}/approve")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_ACCESS_APPROVE')")
    public ApiResponse<ArchiveAccessDtos.AccessData> approve(
            @PathVariable long id,
            @Valid @RequestBody(required = false) ArchiveAccessDtos.ApprovalRequest request,
            Authentication authentication) {
        return ApiResponse.success(service.approve(id, actor(authentication), true,
                request == null ? null : request.comment()), TraceId.current());
    }

    @PostMapping("/api/archive-access-applications/{id}/reject")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_ACCESS_APPROVE')")
    public ApiResponse<ArchiveAccessDtos.AccessData> reject(
            @PathVariable long id,
            @Valid @RequestBody(required = false) ArchiveAccessDtos.ApprovalRequest request,
            Authentication authentication) {
        return ApiResponse.success(service.approve(id, actor(authentication), false,
                request == null ? null : request.comment()), TraceId.current());
    }

    @PostMapping("/api/archive-access-applications/{id}/checkout")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_ACCESS_CHECKOUT')")
    public ApiResponse<ArchiveAccessDtos.UseData> checkout(@PathVariable long id,
                                                            Authentication authentication) {
        return ApiResponse.success(service.checkout(id, actor(authentication)), TraceId.current());
    }

    @GetMapping("/api/archive-access-applications/{id}")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_READ')")
    public ApiResponse<ArchiveAccessDtos.AccessData> get(@PathVariable long id, Authentication authentication) {
        return ApiResponse.success(service.get(id, actor(authentication)), TraceId.current());
    }

    @PostMapping("/api/archive-uses/{id}/return")
    @PreAuthorize("hasAuthority('PERM_ARCHIVE_ACCESS_RETURN')")
    public ApiResponse<ArchiveAccessDtos.UseData> returnUse(
            @PathVariable long id,
            @Valid @RequestBody ArchiveAccessDtos.ReturnRequest request,
            Authentication authentication) {
        return ApiResponse.success(service.returnUse(id, actor(authentication), request), TraceId.current());
    }

    private WorkflowActor actor(Authentication authentication) {
        return WorkflowActor.from(authentication);
    }
}
