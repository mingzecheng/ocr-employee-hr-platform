package com.hrplatform.employee;

public class DataScopeDeniedException extends RuntimeException {
    public DataScopeDeniedException(Long employeeId) {
        super("无权访问员工档案: " + employeeId);
    }
}
