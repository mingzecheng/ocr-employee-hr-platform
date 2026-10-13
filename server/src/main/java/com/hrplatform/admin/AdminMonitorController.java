package com.hrplatform.admin;

import com.hrplatform.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务器监控面板。仅 {@code SYSTEM_ADMIN} 可访问；前端每 5 秒轮询一次。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMonitorController {
    private final MonitorService monitorService;

    public AdminMonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/monitor")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<MonitorSnapshot> monitor() {
        return ApiResponse.success(monitorService.snapshot(), "unknown");
    }
}
