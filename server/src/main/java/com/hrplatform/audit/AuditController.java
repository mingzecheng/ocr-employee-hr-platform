package com.hrplatform.audit;

import com.hrplatform.common.web.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/operation-logs")
public class AuditController {
    private final OperationLogMapper mapper;

    public AuditController(OperationLogMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public ApiResponse<AuditPage> list(@RequestParam(required = false) Long operatorId,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(required = false) String objectType,
                                       @RequestParam(required = false) String result,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int pageSize,
                                       Authentication ignored) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        return ApiResponse.success(new AuditPage(mapper.list(operatorId, action, objectType, result, from, to,
                (safePage - 1) * safeSize, safeSize),
                mapper.count(operatorId, action, objectType, result, from, to), safePage, safeSize), "unknown");
    }
}
