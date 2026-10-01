package com.hrplatform.access;

public record ArchiveAccessItem(Long id, Long applicationId, Long versionId, Long employeeId,
                                Long departmentId, String status) {
}
