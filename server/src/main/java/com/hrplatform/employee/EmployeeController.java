package com.hrplatform.employee;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/employees")
public class EmployeeController {
    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ApiResponse<EmployeeView> create(@Valid @RequestBody EmployeeCreateRequest request, Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(EmployeeView.from(service.create(request, principal.userId()), true), "unknown");
    }

    @PutMapping("/{employeeId}/profile")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ApiResponse<EmployeeView> updateProfile(@PathVariable Long employeeId,
                                                   @Valid @RequestBody EmployeeProfileUpdateRequest request,
                                                   Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        Employee employee = service.updateProfile(employeeId, request, principal.userId());
        return ApiResponse.success(EmployeeView.from(employee, true), "unknown");
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<EmployeeView> get(@PathVariable Long employeeId, Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        Employee employee = service.getRequired(employeeId, principal.dataScope());
        return ApiResponse.success(EmployeeView.from(employee, canReadFullPhone(principal)), "unknown");
    }

    @GetMapping("/{employeeId}/status-history")
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<EmployeeStatusHistoryPage> statusHistory(@PathVariable Long employeeId,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                                  Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.listStatusHistory(employeeId, page, pageSize, principal.dataScope()), "unknown");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN')")
    public ApiResponse<EmployeeSummaryPage> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long departmentId,
                                          @RequestParam(required = false) EmployeeStatus status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int pageSize,
                                          Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        EmployeePage result = service.list(keyword, departmentId, status, page, pageSize, principal.dataScope());
        return ApiResponse.success(new EmployeeSummaryPage(result.items().stream()
                .map(employee -> EmployeeView.from(employee, canReadFullPhone(principal))).toList(),
                result.total(), result.page(), result.pageSize()), "unknown");
    }

    private boolean canReadFullPhone(JwtPrincipal principal) {
        return principal.roles().contains("HR_ADMIN");
    }
}
