package com.hrplatform.archive;

public record ArchiveOcrSource(Long versionId, String objectKey, String originalName,
                               String contentType, long size, String sha256,
                               String documentType) {
}
