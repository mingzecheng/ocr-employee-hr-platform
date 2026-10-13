package com.hrplatform.contract;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService service;

    public ContractController(ContractService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<ContractPage> list(@RequestParam(required = false) Long employeeId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String renewalStatus,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int pageSize,
                                          Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        return ApiResponse.success(service.list(employeeId, status, renewalStatus, page, pageSize,
                principal.dataScope()), "unknown");
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ApiResponse<ContractRecord> create(@Valid @RequestBody ContractCreateRequest request,
                                              Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        return ApiResponse.success(service.create(request, principal.dataScope(), principal.userId()), "unknown");
    }

    @PutMapping("/{id}/renewal-status")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ApiResponse<ContractRecord> markNotRenewing(@PathVariable Long id, Authentication authentication) {
        JwtPrincipal principal = principal(authentication);
        return ApiResponse.success(service.markNotRenewing(id, principal.dataScope(), principal.userId()), "unknown");
    }

    private JwtPrincipal principal(Authentication authentication) {
        return (JwtPrincipal) authentication.getPrincipal();
    }
}
