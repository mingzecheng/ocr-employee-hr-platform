package com.hrplatform.archive.ocr;

public record OcrSourceData(String objectKey, String originalName, String contentType,
                            long size, String sha256) {
}
