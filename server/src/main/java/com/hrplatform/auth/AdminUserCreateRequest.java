package com.hrplatform.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AdminUserCreateRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        Long employeeId,
        Long departmentId,
        boolean enabled,
        @NotEmpty Set<@NotBlank String> roleCodes) {
}
