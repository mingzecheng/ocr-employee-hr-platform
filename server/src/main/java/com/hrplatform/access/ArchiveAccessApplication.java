package com.hrplatform.access;

import java.time.LocalDateTime;

public record ArchiveAccessApplication(Long id, String applicationNo, Long applicantId,
                                       Long departmentId, String purpose, String useType,
                                       LocalDateTime startAt, LocalDateTime dueAt, String status,
                                       int versionNo, LocalDateTime createdAt) {
    public ArchiveAccessApplication withState(String next, int version) {
        return new ArchiveAccessApplication(id, applicationNo, applicantId, departmentId, purpose,
                useType, startAt, dueAt, next, version, createdAt);
    }
}
