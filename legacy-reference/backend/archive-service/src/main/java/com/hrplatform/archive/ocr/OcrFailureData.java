package com.hrplatform.archive.ocr;

import java.time.LocalDateTime;

public record OcrFailureData(Long bindingId, Long versionId, Long employeeId,
                             String taskId, String errorMessage, LocalDateTime updatedAt) {
}
