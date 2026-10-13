package com.hrplatform.ocr;

import java.util.List;

public record OcrResult(Long bindingId, Long versionId, String taskId, String status,
                        String documentType, String engineVersion, long processingDurationMs,
                        boolean reviewRequired, String errorCode, String errorMessage,
                        String previewUrl, List<OcrTextBlock> textBlocks,
                        List<OcrRawField> rawFields, List<BusinessField> fields,
                        List<OcrFieldRevision> revisions) {
    public OcrResult withPreviewUrl(String value) {
        return new OcrResult(bindingId, versionId, taskId, status, documentType, engineVersion,
                processingDurationMs, reviewRequired, errorCode, errorMessage, value,
                textBlocks, rawFields, fields, revisions);
    }
}
