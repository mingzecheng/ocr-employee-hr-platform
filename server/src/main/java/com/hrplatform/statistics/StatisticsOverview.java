package com.hrplatform.statistics;

import java.time.LocalDateTime;

public record StatisticsOverview(long employeeCount, long completeArchiveCount, long archiveCount,
                                 double archiveCompleteness, long workflowCount, long pendingWorkflowCount,
                                 long activeAccessCount, long abnormalAccessCount, long ocrSucceededCount,
                                 long ocrFailedCount, long ocrReviewCount, double averageOcrDurationMs,
                                 LocalDateTime generatedAt) {
}
