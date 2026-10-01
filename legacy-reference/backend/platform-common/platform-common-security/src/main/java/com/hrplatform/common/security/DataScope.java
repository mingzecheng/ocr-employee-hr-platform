package com.hrplatform.common.security;

import java.util.List;

public final class DataScope {
    public enum Type {
        ALL,
        DEPARTMENT,
        EMPLOYEE,
        NONE
    }

    private final Type type;
    private final long userId;
    private final Long employeeId;
    private final Long departmentId;

    private DataScope(Type type, long userId, Long employeeId, Long departmentId) {
        this.type = type;
        this.userId = userId;
        this.employeeId = employeeId;
        this.departmentId = departmentId;
    }

    public static DataScope fromPrincipal(JwtPrincipal principal) {
        if (principal == null) {
            return none(0L);
        }
        List<String> roles = principal.roles();
        if (roles.contains("SYSTEM_ADMIN") || roles.contains("HR_ADMIN")) {
            return all(principal.userId());
        }
        if (roles.contains("DEPT_MANAGER")) {
            return principal.departmentId() == null
                    ? none(principal.userId())
                    : department(principal.departmentId(), principal.userId());
        }
        if (principal.employeeId() != null) {
            return employee(principal.employeeId(), principal.userId());
        }
        return none(principal.userId());
    }

    public static DataScope all(long userId) {
        return new DataScope(Type.ALL, userId, null, null);
    }

    public static DataScope department(long departmentId, long userId) {
        requirePositive(departmentId, "departmentId");
        return new DataScope(Type.DEPARTMENT, userId, null, departmentId);
    }

    public static DataScope employee(long employeeId, long userId) {
        requirePositive(employeeId, "employeeId");
        return new DataScope(Type.EMPLOYEE, userId, employeeId, null);
    }

    public static DataScope none(long userId) {
        return new DataScope(Type.NONE, userId, null, null);
    }

    public Type type() {
        return type;
    }

    public long userId() {
        return userId;
    }

    public Long employeeId() {
        return employeeId;
    }

    public Long departmentId() {
        return departmentId;
    }

    public boolean allowsEmployee(long targetEmployeeId, Long targetDepartmentId) {
        return switch (type) {
            case ALL -> true;
            case DEPARTMENT -> departmentId.equals(targetDepartmentId);
            case EMPLOYEE -> employeeId == targetEmployeeId;
            case NONE -> false;
        };
    }

    public boolean allowsDepartment(Long targetDepartmentId) {
        return switch (type) {
            case ALL -> true;
            case DEPARTMENT -> departmentId.equals(targetDepartmentId);
            case EMPLOYEE, NONE -> false;
        };
    }

    public String cacheKey() {
        return switch (type) {
            case ALL -> "all";
            case DEPARTMENT -> "department:" + departmentId;
            case EMPLOYEE -> "employee:" + employeeId;
            case NONE -> "none:user:" + userId;
        };
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
