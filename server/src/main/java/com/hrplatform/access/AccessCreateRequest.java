package com.hrplatform.access;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record AccessCreateRequest(@NotBlank String purpose, @NotBlank String useType,
                                  LocalDateTime startAt, @NotNull LocalDateTime dueAt,
                                  @NotEmpty List<Long> versionIds) {
}
