package com.hrplatform.workflow;

import java.time.LocalDateTime;
import java.util.List;

public record TodoData(List<TodoItem> items, int total, int pendingApprovalCount,
                       int dueSoonCount, int overdueCount, int ocrFailedCount,
                       LocalDateTime generatedAt) {
}
