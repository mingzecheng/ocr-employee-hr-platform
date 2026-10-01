package com.hrplatform.archive.statistics;

import com.hrplatform.common.security.DataScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {
    private final StatisticsMapper mapper;

    public StatisticsService(StatisticsMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public StatisticsDtos.OverviewData overview(DataScope scope) {
        if (scope.type() == DataScope.Type.NONE) {
            return new StatisticsDtos.OverviewData(0L, 0L, 0L, 0L, 0L, 0.0, 0.0, 0L);
        }
        return toOverview(mapper.aggregateInScope(scope.type().name(), scope.employeeId(), scope.departmentId()));
    }

    private StatisticsDtos.OverviewData toOverview(StatisticsMapper.StatisticsAggregate aggregate) {
        double ocrSuccessRate = aggregate.getOcrTaskCount() == 0
                ? 0.0 : (double) aggregate.getOcrSucceededCount() / aggregate.getOcrTaskCount();
        double completenessRate = aggregate.getActiveEmployeeCount() == 0
                ? 0.0 : (double) aggregate.getCompleteEmployeeCount() / aggregate.getActiveEmployeeCount();
        return new StatisticsDtos.OverviewData(
                aggregate.getActiveEmployeeCount(), aggregate.getArchiveDocumentCount(), aggregate.getOcrTaskCount(),
                aggregate.getOcrSucceededCount(), aggregate.getOcrFailedCount(), ocrSuccessRate,
                completenessRate, aggregate.getPendingReviewCount());
    }
}
