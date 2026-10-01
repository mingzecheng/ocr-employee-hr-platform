package com.hrplatform.ocr;

public class OcrPreviewMissingException extends RuntimeException {
    public OcrPreviewMissingException(String taskId) {
        super("OCR 预览不存在: " + taskId);
    }
}
