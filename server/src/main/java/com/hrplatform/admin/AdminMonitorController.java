package com.hrplatform.admin;

import com.hrplatform.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务器监控面板。仅 {@code SYSTEM_ADMIN} 可访问；前端每 5 秒轮询一次。
 *
 * <p>在注解角色门控之上，再要求当前角色拥有"系统监控"模块权限（sys_role_permission）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMonitorController {
    private final MonitorService monitorService;
    private final RolePermissionService rolePermissionService;

    public AdminMonitorController(MonitorService monitorService, RolePermissionService rolePermissionService) {
        this.monitorService = monitorService;
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping("/monitor")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<MonitorSnapshot> monitor() {
        rolePermissionService.requireModule("系统监控");
        return ApiResponse.success(monitorService.snapshot(), "unknown");
    }
}
