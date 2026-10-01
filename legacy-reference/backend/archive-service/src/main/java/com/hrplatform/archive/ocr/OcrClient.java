package com.hrplatform.archive.ocr;

public interface OcrClient {
    OcrTaskData createTask(OcrSourceData source, String documentType);

    /**
     * Compatibility entry point for callers that still use direct multipart OCR.
     * ArchiveService uses the object-reference overload above.
     */
    default OcrTaskData createTask(String filename, String contentType, byte[] content,
                                   String documentType) {
        throw new UnsupportedOperationException("Direct multipart OCR is not configured");
    }

    OcrTaskData getTask(String taskId);

    byte[] getDetectionPreview(String taskId);

    OcrTaskData correctField(String taskId, String fieldCode, String value,
                             String operatorId, String reason);
}
