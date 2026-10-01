package com.hrplatform.employee;

import java.time.LocalDate;

public record Employee(
        Long id,
        String employeeNo,
        String name,
        String phone,
        Long departmentId,
        Long positionId,
        EmployeeStatus status,
        LocalDate hireDate,
        LocalDate leaveDate
) {
}
