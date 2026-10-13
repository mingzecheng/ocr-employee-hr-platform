package com.hrplatform.statistics;

public record DepartmentStatisticsRow(
        Long departmentId,
        String departmentName,
        long employeeCount,
        long archiveCount,
        long workflowCount,
        long pendingWorkflowCount,
        long abnormalAccessCount
) {
}
