package com.hrplatform.workflow.todo;

import java.time.LocalDateTime;
import java.util.List;

public final class TodoDtos {
    private TodoDtos() {}

    public record TodoData(List<TodoItem> items, int total, int pendingApprovalCount,
                           int dueSoonCount, int overdueCount, int ocrFailedCount,
                           LocalDateTime generatedAt) {}

    public record TodoItem(Long id, String type, String title, Long resourceId, String status,
                           String priority, LocalDateTime dueAt, LocalDateTime createdAt,
                           String targetPath) {}

    public record OcrFailureItem(Long bindingId, Long versionId, Long employeeId, String taskId,
                                 String errorMessage, LocalDateTime updatedAt) {}
}
