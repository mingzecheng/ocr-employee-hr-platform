package com.hrplatform.archive.api;

public class EmployeeExistsException extends RuntimeException {
    public EmployeeExistsException(String employeeNo) {
        super("Employee number already exists: " + employeeNo);
    }
}
