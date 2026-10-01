package com.hrplatform.ocr;

import java.time.LocalDateTime;

public record OcrFieldRevision(Long id, Long bindingId, String fieldCode, String previousValue,
                               String currentValue, String reason, Long operatorId,
                               LocalDateTime createdAt) {
}
