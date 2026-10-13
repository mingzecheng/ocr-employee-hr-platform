package com.hrplatform.auth;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AdminUserUpdateRequest(
        Long employeeId,
        Long departmentId,
        boolean enabled,
        @Size(min = 8, max = 72) String password,
        @NotEmpty Set<String> roleCodes) {
}
