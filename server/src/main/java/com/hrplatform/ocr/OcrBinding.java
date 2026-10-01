package com.hrplatform.ocr;

import java.time.LocalDateTime;

public record OcrBinding(Long id, Long versionId, String taskId, String status, String engineVersion,
                         long processingDurationMs, boolean reviewRequired, String errorCode,
                         LocalDateTime createdAt, String documentType, String previewUrl,
                         String previewContentType, Long previewSize, String previewSha256,
                         Integer previewWidth, Integer previewHeight, String errorMessage,
                         LocalDateTime updatedAt) {
    public OcrBinding(Long id, Long versionId, String taskId, String status, String engineVersion,
                      long processingDurationMs, boolean reviewRequired, String errorCode,
                      LocalDateTime createdAt) {
        this(id, versionId, taskId, status, engineVersion, processingDurationMs, reviewRequired,
                errorCode, createdAt, null, null, null, null, null, null, null, null, createdAt);
    }

    public OcrBinding(Long id, Long versionId, String taskId, String status, String engineVersion,
                      long processingDurationMs, boolean reviewRequired, String errorCode,
                      LocalDateTime createdAt, String documentType) {
        this(id, versionId, taskId, status, engineVersion, processingDurationMs, reviewRequired,
                errorCode, createdAt, documentType, null, null, null, null, null, null, null, createdAt);
    }
}
