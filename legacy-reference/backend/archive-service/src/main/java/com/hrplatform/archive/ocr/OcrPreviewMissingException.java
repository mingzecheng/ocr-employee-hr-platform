package com.hrplatform.archive.ocr;

public class OcrPreviewMissingException extends RuntimeException {
    public OcrPreviewMissingException(String taskId) {
        super("OCR detection preview not found: " + taskId);
    }
}
