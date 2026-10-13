package com.hrplatform.statistics;

public record StatisticsTrendPoint(
        String period,
        long employeeCount,
        long workflowCount,
        long ocrSucceededCount,
        long ocrFailedCount
) {
}
