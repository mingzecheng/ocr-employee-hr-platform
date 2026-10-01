package com.hrplatform.archive.statistics;

import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.api.TraceId;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.common.security.JwtPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatisticsController {
    private final StatisticsService service;

    public StatisticsController(StatisticsService service) {
        this.service = service;
    }

    @GetMapping("/api/statistics/overview")
    @PreAuthorize("hasAuthority('PERM_STATISTICS_READ')")
    public ApiResponse<StatisticsDtos.OverviewData> overview(org.springframework.security.core.Authentication authentication) {
        DataScope scope = authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal
                ? DataScope.fromPrincipal(principal) : DataScope.none(0L);
        return ApiResponse.success(service.overview(scope), TraceId.current());
    }
}
