package com.hrplatform.employee;

import java.time.LocalDateTime;

public record EmployeeStatusHistory(Long employeeId, String beforeStatus, String afterStatus,
                                    Long beforeDepartmentId, Long afterDepartmentId,
                                    Long beforePositionId, Long afterPositionId,
                                    Long sourceRequestId, Long operatorId,
                                    LocalDateTime createdAt) {
}
