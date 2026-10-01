package com.hrplatform.employee;

import java.time.LocalDate;

public record EmployeeMutation(Long id, Long departmentId, Long positionId, EmployeeStatus status,
                               LocalDate hireDate, LocalDate leaveDate) {
}
