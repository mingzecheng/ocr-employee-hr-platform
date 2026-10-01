package com.hrplatform.workflow;

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
    public ApiResponse<HrRequest> create(@Valid @RequestBody RequestCreateRequest request,
                                         Authentication authentication) {
        return ApiResponse.success(service.create(request, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<HrRequest> submit(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.submit(id, actor(authentication)), "unknown");
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<HrRequest> approve(@PathVariable Long id,
                                          @RequestBody(required = false) ApprovalRequest request,
                                          Authentication authentication) {
        return ApiResponse.success(service.approve(id, actor(authentication), request == null ? null : request.comment()),
                "unknown");
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<HrRequest> reject(@PathVariable Long id,
                                         @RequestBody(required = false) ApprovalRequest request,
                                         Authentication authentication) {
        return ApiResponse.success(service.reject(id, actor(authentication), request == null ? null : request.comment()),
                "unknown");
    }

    @GetMapping
    public ApiResponse<RequestPage> list(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long employeeId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize,
                                         Authentication authentication) {
        return ApiResponse.success(service.list(type, status, employeeId, page, pageSize, actor(authentication)),
                "unknown");
    }

    private RequestActor actor(Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return new RequestActor(principal.userId(), principal.roles(), principal.dataScope());
    }

    public record ApprovalRequest(@Size(max = 512) String comment) {
    }
}
