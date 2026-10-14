package com.hrplatform.admin;

import com.hrplatform.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 在线用户管理。仅 {@code SYSTEM_ADMIN} 可访问。
 *
 * <p>列表接口在角色门控之上，再要求"在线用户"模块权限。
 */
@RestController
@RequestMapping("/api/admin/online-users")
public class AdminOnlineUserController {
    private final OnlineUserService onlineUserService;
    private final RolePermissionService rolePermissionService;

    public AdminOnlineUserController(OnlineUserService onlineUserService,
                                     RolePermissionService rolePermissionService) {
        this.onlineUserService = onlineUserService;
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<OnlineUserRow>> list() {
        rolePermissionService.requireModule(RolePermissionService.MODULE_ONLINE_USERS);
        return ApiResponse.success(onlineUserService.listOnlineUsers(), "unknown");
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> forceOffline(@PathVariable Long userId) {
        int removed = onlineUserService.forceOffline(userId);
        return ApiResponse.success(Map.of("userId", userId, "removedRefreshTokens", removed), "unknown");
    }
}
