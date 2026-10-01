package com.hrplatform.ocr;

public record OcrCreateRequest(
        String sourceObjectKey,
        String originalName,
        String contentType,
        long size,
        String sha256,
        String documentType
) {
}
