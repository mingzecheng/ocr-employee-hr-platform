package com.hrplatform.statistics;

import com.hrplatform.common.security.DataScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
}
