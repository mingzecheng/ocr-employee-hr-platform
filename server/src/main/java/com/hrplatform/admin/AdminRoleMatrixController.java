package com.hrplatform.admin;

import com.hrplatform.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色权限矩阵（只读）。仅 {@code SYSTEM_ADMIN} 可访问，不提供编辑接口。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminRoleMatrixController {

    @GetMapping("/roles")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<RoleMatrixView> roles() {
        return ApiResponse.success(
                new RoleMatrixView(RoleMatrix.ROLES, RoleMatrix.MODULES, RoleMatrix.matrix()), "unknown");
    }
}
