package com.hrplatform.archive;

import java.time.LocalDateTime;

public record ArchiveVersion(
        Long id,
        Long documentId,
        int versionNo,
        Long fileObjectId,
        String originalName,
        String contentType,
        long size,
        String sha256,
        String status,
        boolean isCurrent,
        LocalDateTime createdAt,
        Long createdBy,
        String changeReason
) {
}
