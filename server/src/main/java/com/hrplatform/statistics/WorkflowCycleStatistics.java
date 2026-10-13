package com.hrplatform.statistics;

public record WorkflowCycleStatistics(
        long completedCount,
        double averageDurationHours,
        double shortestDurationHours,
        double longestDurationHours
) {
    public static WorkflowCycleStatistics empty() {
        return new WorkflowCycleStatistics(0, 0, 0, 0);
    }
}
