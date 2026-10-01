package com.hrplatform.workflow.access;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ArchiveAccessDtos {
    private ArchiveAccessDtos() {}

    public record CreateRequest(
            @NotNull @Positive Long employeeId,
            @NotBlank String useType,
            @NotBlank @Size(max = 512) String purpose,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime dueAt,
            Boolean returnRequired,
            @NotEmpty List<@Valid ItemRequest> items) {}

    public record ItemRequest(
            @NotNull @Positive Long archiveDocumentId,
            Long archiveVersionId,
            @NotBlank @Size(max = 32) String scope) {}

    public record ApprovalRequest(@Size(max = 512) String comment) {}

    public record ReturnRequest(
            @NotBlank String returnType,
            Long handoverToId,
            @Size(max = 1024) String missingDescription,
            @Size(max = 1024) String damageDescription) {}

    public record AccessData(Long id, String applicationNo, Long applicantId, Long employeeId,
                             String useType, String purpose, LocalDateTime startAt,
                             LocalDateTime dueAt, Boolean returnRequired, String status,
                             String currentNode, List<ItemData> items,
                             List<ApprovalData> approvals, UseData use) {}

    public record ItemData(Long id, Long archiveDocumentId, Long archiveVersionId, String scope) {}

    public record ApprovalData(Long id, String nodeCode, Long approverId, String decision,
                               String comment, LocalDateTime decidedAt) {}

    public record UseData(Long id, Long applicationId, Long receiverId, LocalDateTime checkedOutAt,
                          LocalDateTime dueAt, LocalDateTime returnedAt, String returnType,
                          Long handoverToId, String missingDescription, String damageDescription,
                          String status) {}
}
