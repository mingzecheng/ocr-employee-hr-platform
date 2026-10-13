package com.hrplatform.organization;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DepartmentMutationRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,31}") String code,
        @NotBlank @Size(max = 128) String name,
        @Positive Long parentId,
        @Positive Long managerEmployeeId,
        @NotNull @Min(0) @Max(100000) Integer sortOrder,
        @NotNull Boolean enabled) {
}
