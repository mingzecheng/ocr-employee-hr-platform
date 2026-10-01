package com.hrplatform.workflow.request;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestController {
    private final RequestService service;

    public RequestController(RequestService service) { this.service = service; }

    @PostMapping("/api/hr-requests")
    @PreAuthorize("hasAuthority('PERM_HR_REQUEST_CREATE')")
    public ApiResponse<RequestDtos.RequestData> create(@Valid @RequestBody RequestDtos.CreateRequest request,
                                                       Authentication authentication) {
        return ApiResponse.success(service.create(request, actor(authentication)), TraceId.current());
    }

    @PostMapping("/api/hr-requests/{id}/submit")
    @PreAuthorize("hasAuthority('PERM_HR_REQUEST_CREATE')")
    public ApiResponse<RequestDtos.RequestData> submit(@PathVariable long id, Authentication authentication) {
        return ApiResponse.success(service.submit(id, actor(authentication)), TraceId.current());
    }

    @PostMapping("/api/approvals/{id}/approve")
    @PreAuthorize("hasAuthority('PERM_HR_REQUEST_APPROVE')")
    public ApiResponse<RequestDtos.RequestData> approve(@PathVariable long id,
                                                        @Valid @RequestBody(required = false) RequestDtos.ApprovalRequest request,
                                                        Authentication authentication) {
        return ApiResponse.success(service.approve(id, actor(authentication), true,
                request == null ? null : request.comment()), TraceId.current());
    }

    @PostMapping("/api/approvals/{id}/reject")
    @PreAuthorize("hasAuthority('PERM_HR_REQUEST_APPROVE')")
    public ApiResponse<RequestDtos.RequestData> reject(@PathVariable long id,
                                                       @Valid @RequestBody(required = false) RequestDtos.ApprovalRequest request,
                                                       Authentication authentication) {
        return ApiResponse.success(service.approve(id, actor(authentication), false,
                request == null ? null : request.comment()), TraceId.current());
    }

    @GetMapping("/api/hr-requests/{id}")
    @PreAuthorize("hasAuthority('PERM_HR_REQUEST_READ')")
    public ApiResponse<RequestDtos.RequestData> get(@PathVariable long id, Authentication authentication) {
        return ApiResponse.success(service.get(id, actor(authentication)), TraceId.current());
    }

    private WorkflowActor actor(Authentication authentication) {
        return WorkflowActor.from(authentication);
    }
}
