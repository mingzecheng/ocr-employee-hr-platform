package com.hrplatform.common.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record DataScope(Type type, Long userId, Long employeeId, Long departmentId) {
    public enum Type { NONE, EMPLOYEE, DEPARTMENT, ALL }

    public static DataScope fromClaims(Long userId, Long employeeId, Long departmentId, Set<String> roles) {
        Set<String> roleSet = roles == null ? Collections.emptySet() : new HashSet<>(roles);
        if (roleSet.contains("SYSTEM_ADMIN") || roleSet.contains("HR_ADMIN")) {
            return new DataScope(Type.ALL, userId, employeeId, departmentId);
        }
        if (roleSet.contains("DEPT_MANAGER") && departmentId != null) {
            return new DataScope(Type.DEPARTMENT, userId, employeeId, departmentId);
        }
        if (roleSet.contains("EMPLOYEE") && employeeId != null) {
            return new DataScope(Type.EMPLOYEE, userId, employeeId, departmentId);
        }
        return new DataScope(Type.NONE, userId, employeeId, departmentId);
    }

    public boolean canAccess(Long targetEmployeeId, Long targetDepartmentId) {
        return switch (type) {
            case ALL -> true;
            case DEPARTMENT -> departmentId != null && departmentId.equals(targetDepartmentId);
            case EMPLOYEE -> employeeId != null && employeeId.equals(targetEmployeeId);
            case NONE -> false;
        };
    }
}
