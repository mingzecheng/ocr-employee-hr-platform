package com.hrplatform.workflow;

import java.time.LocalDateTime;

public record TodoItem(Long id, String type, String title, Long resourceId, String status,
                       String priority, LocalDateTime dueAt, LocalDateTime createdAt,
                       String targetPath) {
}
