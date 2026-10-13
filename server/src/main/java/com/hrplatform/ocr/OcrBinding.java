package com.hrplatform.ocr;

import java.time.LocalDateTime;

public record OcrBinding(Long id, Long versionId, String taskId, String status, String engineVersion,
                         long processingDurationMs, boolean reviewRequired, String errorCode,
                         LocalDateTime createdAt, String documentType, String previewUrl,
                         String previewContentType, Long previewSize, String previewSha256,
                         Integer previewWidth, Integer previewHeight, String errorMessage,
                         LocalDateTime updatedAt,
                         String confirmStatus, Long confirmedBy, LocalDateTime confirmedAt,
                         String confirmNote) {
    public OcrBinding(Long id, Long versionId, String taskId, String status, String engineVersion,
                      long processingDurationMs, boolean reviewRequired, String errorCode,
                      LocalDateTime createdAt) {
        this(id, versionId, taskId, status, engineVersion, processingDurationMs, reviewRequired,
                errorCode, createdAt, null, null, null, null, null, null, null, null, createdAt,
                "PENDING", null, null, null);
    }

    public OcrBinding(Long id, Long versionId, String taskId, String status, String engineVersion,
                      long processingDurationMs, boolean reviewRequired, String errorCode,
                      LocalDateTime createdAt, String documentType) {
        this(id, versionId, taskId, status, engineVersion, processingDurationMs, reviewRequired,
                errorCode, createdAt, documentType, null, null, null, null, null, null, null, createdAt,
                "PENDING", null, null, null);
    }

    /**
     * 拷贝一份仅替换确认状态相关字段的绑定记录，用于 confirm/reject 流程。
     */
    public OcrBinding withConfirm(String confirmStatus, Long confirmedBy, LocalDateTime confirmedAt,
                                  String confirmNote) {
        return new OcrBinding(id, versionId, taskId, status, engineVersion, processingDurationMs,
                reviewRequired, errorCode, createdAt, documentType, previewUrl, previewContentType,
                previewSize, previewSha256, previewWidth, previewHeight, errorMessage, updatedAt,
                confirmStatus, confirmedBy, confirmedAt, confirmNote);
    }
}
