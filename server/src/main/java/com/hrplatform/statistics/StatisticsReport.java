package com.hrplatform.statistics;

import java.util.List;

public record StatisticsReport(
        List<StatisticsTrendPoint> monthlyTrend,
        List<DepartmentStatisticsRow> departments,
        WorkflowCycleStatistics workflowCycle
) {
    public StatisticsReport {
        monthlyTrend = monthlyTrend == null ? List.of() : List.copyOf(monthlyTrend);
        departments = departments == null ? List.of() : List.copyOf(departments);
        workflowCycle = workflowCycle == null ? WorkflowCycleStatistics.empty() : workflowCycle;
    }
}
