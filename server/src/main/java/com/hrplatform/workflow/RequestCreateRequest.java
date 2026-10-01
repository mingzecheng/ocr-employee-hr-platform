package com.hrplatform.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RequestCreateRequest(@NotBlank String requestType, @NotNull @Positive Long employeeId,
                                   Long targetDepartmentId, Long targetPositionId,
                                   Long sourceVersionId, String payloadJson) {
}
