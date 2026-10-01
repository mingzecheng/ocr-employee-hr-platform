package com.hrplatform.workflow.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 32) String scopeType,
            @Size(max = 128) String scopeValue,
            @NotEmpty List<@Valid ItemRequest> items) {}

    public record ItemRequest(
            @NotNull @Positive Long archiveDocumentId,
            @NotNull @Positive Long expectedVersionId) {}

    public record CheckRequest(
            Long actualVersionId,
            @NotBlank @Size(max = 16) String actualStatus,
            @Size(max = 1024) String differenceDescription) {}

    public record ResolveRequest(@NotBlank @Size(max = 1024) String resolution) {}

    public record TaskData(Long id, String taskNo, String scopeType, String scopeValue,
                           Long initiatorId, String status, LocalDateTime startedAt,
                           LocalDateTime completedAt, List<ItemData> items,
                           List<ResolutionData> resolutions) {}

    public record ItemData(Long id, Long archiveDocumentId, Long expectedVersionId,
                           Long actualVersionId, String actualStatus, String differenceType,
                           String differenceDescription, Long checkedBy,
                           LocalDateTime checkedAt) {}

    public record ResolutionData(Long id, Long operatorId, String resolution,
                                 LocalDateTime createdAt) {}
}
