package com.hrplatform.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EmployeeCreateRequest(
        @NotBlank String employeeNo,
        @NotBlank String name,
        String phone,
        @NotNull Long departmentId,
        @NotNull Long positionId,
        @NotNull EmployeeStatus status,
        LocalDate hireDate
) {
}
