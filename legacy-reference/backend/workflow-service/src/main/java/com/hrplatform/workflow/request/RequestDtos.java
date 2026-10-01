package com.hrplatform.workflow.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class RequestDtos {
    private RequestDtos() {}

    public record CreateRequest(
            @NotBlank String requestType,
            @NotNull @Positive Long employeeId,
            JsonNode payload) {}

    public record ApprovalRequest(@Size(max = 512) String comment) {}

    public record RequestData(Long id, String requestNo, String requestType, Long employeeId,
                              Long applicantId, JsonNode payload, String status, String currentNode) {}
}
