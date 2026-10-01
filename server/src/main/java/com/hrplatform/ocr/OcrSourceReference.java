package com.hrplatform.ocr;

public record OcrSourceReference(Long versionId, String objectKey, String originalName,
                                 String contentType, long size, String sha256,
                                 String documentType) {
}
