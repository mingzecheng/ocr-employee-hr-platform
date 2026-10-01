package com.hrplatform.archive;

public record ArchiveOcrSource(Long versionId, String objectKey, String originalName,
                               String contentType, long size, String sha256,
                               String documentType, Long employeeId, Long departmentId) {
    public ArchiveOcrSource(Long versionId, String objectKey, String originalName,
                            String contentType, long size, String sha256, String documentType) {
        this(versionId, objectKey, originalName, contentType, size, sha256, documentType, null, null);
    }
}
