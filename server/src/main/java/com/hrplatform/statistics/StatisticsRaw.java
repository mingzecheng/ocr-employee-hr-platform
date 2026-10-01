package com.hrplatform.statistics;

public record StatisticsRaw(long employeeCount, long completeArchiveCount, long archiveCount,
                            long workflowCount, long pendingWorkflowCount, long activeAccessCount,
                            long abnormalAccessCount, long ocrSucceededCount, long ocrFailedCount,
                            long ocrReviewCount, long ocrDurationTotalMs) {
    public static StatisticsRaw empty() {
        return new StatisticsRaw(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
