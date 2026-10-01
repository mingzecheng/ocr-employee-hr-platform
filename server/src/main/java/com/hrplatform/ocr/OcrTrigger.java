package com.hrplatform.ocr;

public record OcrTrigger(Long versionId, String sourceObjectKey, String originalName, String contentType,
                         long size, String sha256, String documentType, Long operatorId) {
}
