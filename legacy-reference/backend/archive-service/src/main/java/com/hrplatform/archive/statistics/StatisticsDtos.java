package com.hrplatform.archive.statistics;

public final class StatisticsDtos {
    private StatisticsDtos() {}

    public record OverviewData(long activeEmployeeCount,
                               long archiveDocumentCount,
                               long ocrTaskCount,
                               long ocrSucceededCount,
                               long ocrFailedCount,
                               double ocrSuccessRate,
                               double archiveCompletenessRate,
                               long pendingReviewCount) {
    }
}
