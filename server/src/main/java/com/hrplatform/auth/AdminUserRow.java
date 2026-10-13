package com.hrplatform.auth;

public record AdminUserRow(Long id, String username, Long employeeId, Long departmentId, boolean enabled) {
}
