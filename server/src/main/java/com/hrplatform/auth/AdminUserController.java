package com.hrplatform.auth;

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

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<AdminUserPage> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(service.list(page, pageSize), "unknown");
    }

    @GetMapping("/roles")
    public ApiResponse<List<AdminRoleOption>> roles() {
        return ApiResponse.success(service.roles(), "unknown");
    }

    @PostMapping
    public ApiResponse<AdminUserView> create(@Valid @RequestBody AdminUserCreateRequest request,
                                             Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.create(request, principal.userId()), "unknown");
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminUserView> update(@PathVariable Long id,
                                             @Valid @RequestBody AdminUserUpdateRequest request,
                                             Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.update(id, request, principal.userId()), "unknown");
    }
}
