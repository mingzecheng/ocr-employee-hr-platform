package com.hrplatform.employee;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    public ApiResponse<Employee> create(@Valid @RequestBody EmployeeCreateRequest request, Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.create(request, principal.userId()), "unknown");
    }

    @GetMapping("/{employeeId}")
    public ApiResponse<Employee> get(@PathVariable Long employeeId, Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.getRequired(employeeId, principal.dataScope()), "unknown");
    }

    @GetMapping
    public ApiResponse<EmployeePage> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long departmentId,
                                          @RequestParam(required = false) EmployeeStatus status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int pageSize,
                                          Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.list(keyword, departmentId, status, page, pageSize,
                principal.dataScope()), "unknown");
    }
}
