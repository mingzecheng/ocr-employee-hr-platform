package com.hrplatform.admin;

import com.hrplatform.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色权限矩阵。仅 {@code SYSTEM_ADMIN} 可访问。
 *
 * <p>GET 返回由 sys_role_permission 动态驱动的矩阵（启动为空时按静态默认种子化）；
 * PUT 可逐格编辑某角色对某模块的授权（写 sys_role_permission）。
 * 这是后台模块可见性，不影响业务接口的注解级鉴权。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminRoleMatrixController {

    private final RolePermissionService rolePermissionService;

    public AdminRoleMatrixController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<RoleMatrixView> roles() {
        return ApiResponse.success(rolePermissionService.matrix(), "unknown");
    }

    @PutMapping("/roles/{roleCode}/permissions")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> updatePermission(@PathVariable String roleCode,
                                              @RequestBody RolePermissionUpdateRequest body) {
        rolePermissionService.updatePermission(roleCode, body.module(), Boolean.TRUE.equals(body.allowed()));
        return ApiResponse.success(null, "unknown");
    }
}
