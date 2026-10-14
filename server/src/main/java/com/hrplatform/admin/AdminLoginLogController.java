package com.hrplatform.admin;

import com.hrplatform.audit.OperationLogMapper;
import com.hrplatform.audit.OperationLogRecord;
import com.hrplatform.auth.SysUser;
import com.hrplatform.auth.SysUserMapper;
import com.hrplatform.common.web.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录日志：从 operation_log 中筛选 LOGIN / LOGOUT / REFRESH，与通用审计日志分开。
 * 仅 {@code SYSTEM_ADMIN} 可访问，保留 IP / UserAgent 原文。
 *
 * <p>在角色门控之上，再要求"登录日志"模块权限。
 */
@RestController
@RequestMapping("/api/admin/login-logs")
public class AdminLoginLogController {
    private final OperationLogMapper operationLogMapper;
    private final SysUserMapper userMapper;
    private final RolePermissionService rolePermissionService;

    /** 测试兼容构造：不解析 operatorName，也不挂模块守卫。 */
    public AdminLoginLogController(OperationLogMapper operationLogMapper) {
        this(operationLogMapper, null, null);
    }

    @Autowired
    public AdminLoginLogController(OperationLogMapper operationLogMapper, SysUserMapper userMapper,
                                   RolePermissionService rolePermissionService) {
        this.operationLogMapper = operationLogMapper;
        this.userMapper = userMapper;
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<LoginLogPage> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        if (rolePermissionService != null) {
            rolePermissionService.requireModule(RolePermissionService.MODULE_LOGIN_LOGS);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<LoginLogItem> items = operationLogMapper
                .listLoginLogs((safePage - 1) * safeSize, safeSize).stream()
                .map(this::toItem)
                .toList();
        long total = operationLogMapper.countLoginLogs();
        return ApiResponse.success(new LoginLogPage(items, total, safePage, safeSize), "unknown");
    }

    private LoginLogItem toItem(OperationLogRecord record) {
        String username = resolveUsername(record.operatorId());
        return new LoginLogItem(record.id(), username, record.action(), record.clientIp(),
                record.userAgent(), record.result(), record.createdAt());
    }

    private String resolveUsername(Long operatorId) {
        if (userMapper == null || operatorId == null) {
            return null;
        }
        try {
            SysUser user = userMapper.findById(operatorId);
            return user == null ? null : user.username();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
