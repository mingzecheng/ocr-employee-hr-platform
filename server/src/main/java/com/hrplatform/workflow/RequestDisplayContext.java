package com.hrplatform.workflow;

public record RequestDisplayContext(String employeeNo, String employeeName,
                                    Long employeeDepartmentId, String employeeDepartmentName,
                                    Long employeePositionId, String employeePositionName,
                                    String applicantName, String approvalDepartmentName,
                                    String targetDepartmentName, String targetPositionName) {
}
