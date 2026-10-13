package com.hrplatform.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PositionMutationRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,31}") String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull Boolean enabled) {
}
