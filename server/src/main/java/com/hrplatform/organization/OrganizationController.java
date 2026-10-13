package com.hrplatform.organization;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class OrganizationController {
    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<List<DepartmentNode>> tree() {
        return ApiResponse.success(service.departmentTree(), "unknown");
    }

    @GetMapping("/{departmentId}/positions")
    @PreAuthorize("hasAnyRole('EMPLOYEE','DEPT_MANAGER','HR_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<List<PositionOption>> positions(@PathVariable Long departmentId) {
        return ApiResponse.success(service.positions(departmentId), "unknown");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<List<DepartmentAdminView>> adminDepartments() {
        return ApiResponse.success(service.adminDepartments(), "unknown");
    }

    @GetMapping("/{departmentId}/positions/admin")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<List<PositionAdminView>> adminPositions(@PathVariable Long departmentId) {
        return ApiResponse.success(service.adminPositions(departmentId), "unknown");
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<DepartmentAdminView> createDepartment(
            @Valid @RequestBody DepartmentMutationRequest request, Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.createDepartment(request, principal.userId()), "unknown");
    }

    @PutMapping("/{departmentId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<DepartmentAdminView> updateDepartment(
            @PathVariable Long departmentId,
            @Valid @RequestBody DepartmentMutationRequest request,
            Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.updateDepartment(departmentId, request, principal.userId()), "unknown");
    }

    @PostMapping("/{departmentId}/positions")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<PositionAdminView> createPosition(
            @PathVariable Long departmentId,
            @Valid @RequestBody PositionMutationRequest request,
            Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.createPosition(departmentId, request, principal.userId()), "unknown");
    }

    @PutMapping("/{departmentId}/positions/{positionId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ApiResponse<PositionAdminView> updatePosition(
            @PathVariable Long departmentId,
            @PathVariable Long positionId,
            @Valid @RequestBody PositionMutationRequest request,
            Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(
                service.updatePosition(departmentId, positionId, request, principal.userId()), "unknown");
    }

    @DeleteMapping("/{departmentId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> deleteDepartment(@PathVariable Long departmentId, Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        service.deleteDepartment(departmentId, principal.userId());
        return ApiResponse.success(null, "unknown");
    }

    @DeleteMapping("/{departmentId}/positions/{positionId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> deletePosition(
            @PathVariable Long departmentId,
            @PathVariable Long positionId,
            Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        service.deletePosition(departmentId, positionId, principal.userId());
        return ApiResponse.success(null, "unknown");
    }
}
