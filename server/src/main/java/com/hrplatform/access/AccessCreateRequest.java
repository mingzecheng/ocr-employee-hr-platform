package com.hrplatform.access;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record AccessCreateRequest(@NotBlank @Size(max = 512) String purpose,
                                  @NotBlank @Pattern(regexp = "READ|BORROW|HANDOVER", flags = Pattern.Flag.CASE_INSENSITIVE) String useType,
                                  LocalDateTime startAt, @NotNull LocalDateTime dueAt,
                                  @NotEmpty List<Long> versionIds) {
}
