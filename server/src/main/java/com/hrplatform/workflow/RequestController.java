package com.hrplatform.workflow;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hr-requests")
public class RequestController {
    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<HrRequest> create(@Valid @RequestBody RequestCreateRequest request,
                                         Authentication authentication) {
        return ApiResponse.success(service.create(request, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<HrRequest> submit(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.submit(id, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<HrRequest> cancel(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.cancel(id, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<HrRequest> approve(@PathVariable Long id,
                                          @RequestBody(required = false) ApprovalRequest request,
                                          Authentication authentication) {
        return ApiResponse.success(service.approve(id, actor(authentication), request == null ? null : request.comment()),
                "unknown");
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<HrRequest> reject(@PathVariable Long id,
                                         @RequestBody(required = false) ApprovalRequest request,
                                         Authentication authentication) {
        return ApiResponse.success(service.reject(id, actor(authentication), request == null ? null : request.comment()),
                "unknown");
    }

    @PutMapping("/{id}/handover")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ApiResponse<HrRequest> completeHandover(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.completeHandover(id, actor(authentication)), "unknown");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<RequestPage> list(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long employeeId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize,
                                         Authentication authentication) {
        return ApiResponse.success(service.list(type, status, employeeId, page, pageSize, actor(authentication)),
                "unknown");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<RequestDetail> detail(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.detail(id, actor(authentication)), "unknown");
    }

    private RequestActor actor(Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return new RequestActor(principal.userId(), principal.roles(), principal.dataScope());
    }

    public record ApprovalRequest(@Size(max = 512) String comment) {
    }
}
