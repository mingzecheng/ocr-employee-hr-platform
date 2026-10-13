package com.hrplatform.employee;

import java.time.LocalDate;

public record EmployeeView(
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
    public static EmployeeView from(Employee employee, boolean revealPhone) {
        String phone = employee.phone();
        if (!revealPhone && phone != null && !phone.isBlank()) {
            phone = phone.matches("1[3-9]\\d{9}")
                    ? phone.substring(0, 3) + "****" + phone.substring(7)
                    : "****";
        }
        return new EmployeeView(employee.id(), employee.employeeNo(), employee.name(), phone,
                employee.departmentId(), employee.positionId(), employee.status(),
                employee.hireDate(), employee.leaveDate());
    }
}
