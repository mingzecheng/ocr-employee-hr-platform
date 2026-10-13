package com.hrplatform.contract;

import com.hrplatform.common.security.JwtPrincipal;
import com.hrplatform.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contract-reminders")
public class ContractReminderController {
    private final ContractReminderService service;

    public ContractReminderController(ContractReminderService service) {
        this.service = service;
    }

    @PostMapping("/{id}/ack")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ApiResponse<Void> ack(@PathVariable Long id, Authentication authentication) {
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        service.ack(id, principal.userId());
        return ApiResponse.success(null, "unknown");
    }
}
