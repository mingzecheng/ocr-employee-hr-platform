package com.hrplatform.employee;

import java.time.LocalDateTime;

public record EmployeeStatusHistoryView(
        Long id,
        Long employeeId,
        String beforeStatus,
        String afterStatus,
        Long beforeDepartmentId,
        String beforeDepartmentName,
        Long afterDepartmentId,
        String afterDepartmentName,
        Long beforePositionId,
        String beforePositionName,
        Long afterPositionId,
        String afterPositionName,
        Long sourceRequestId,
        String requestNo,
        Long operatorId,
        String operatorName,
        LocalDateTime createdAt
) {
}
