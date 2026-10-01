package com.hrplatform.employee;

public class EmployeeExistsException extends RuntimeException {
    public EmployeeExistsException(String employeeNo) {
        super("员工编号已存在: " + employeeNo);
    }
}
