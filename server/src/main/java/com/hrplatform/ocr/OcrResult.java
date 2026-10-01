package com.hrplatform.ocr;

import java.util.List;

public record OcrResult(Long bindingId, Long versionId, String taskId, String status,
                        String documentType, String engineVersion, long processingDurationMs,
                        boolean reviewRequired, String errorCode, String errorMessage,
                        String previewUrl, List<OcrTextBlock> textBlocks,
                        List<OcrRawField> rawFields, List<BusinessField> fields,
                        List<OcrFieldRevision> revisions) {
}
