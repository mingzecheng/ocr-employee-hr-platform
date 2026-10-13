package com.hrplatform.statistics;

import com.hrplatform.common.security.DataScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StatisticsService {
    private final StatisticsMapper mapper;

    public StatisticsService(StatisticsMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public StatisticsOverview overview(DataScope scope, LocalDate from, LocalDate to) {
        StatisticsRaw raw = mapper.aggregate(scope.type().name(), scope.employeeId(), scope.departmentId(), from, to);
        if (raw == null) {
            raw = StatisticsRaw.empty();
        }
        double completeness = raw.archiveCount() == 0 ? 0.0
                : (double) raw.completeArchiveCount() / raw.archiveCount();
        long ocrTasks = raw.ocrSucceededCount() + raw.ocrFailedCount();
        double avgDuration = ocrTasks == 0 ? 0.0 : (double) raw.ocrDurationTotalMs() / ocrTasks;
        return new StatisticsOverview(raw.employeeCount(), raw.completeArchiveCount(), raw.archiveCount(), completeness,
                raw.workflowCount(), raw.pendingWorkflowCount(), raw.activeAccessCount(), raw.abnormalAccessCount(),
                raw.ocrSucceededCount(), raw.ocrFailedCount(), raw.ocrReviewCount(), avgDuration, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public StatisticsReport report(DataScope scope, LocalDate from, LocalDate to) {
        String scopeType = scope.type().name();
        List<StatisticsTrendPoint> trend = mapper.monthlyTrend(scopeType, scope.employeeId(), scope.departmentId(), from, to);
        List<DepartmentStatisticsRow> departments = mapper.departments(scopeType, scope.employeeId(), scope.departmentId(), from, to);
        WorkflowCycleStatistics cycle = mapper.workflowCycle(scopeType, scope.employeeId(), scope.departmentId(), from, to);
        return new StatisticsReport(trend, departments, cycle);
    }
}
