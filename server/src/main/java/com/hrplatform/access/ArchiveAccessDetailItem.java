package com.hrplatform.access;

public record ArchiveAccessDetailItem(Long id, Long applicationId, Long versionId, int versionNo,
                                      Long employeeId, String employeeNo, String employeeName,
                                      Long departmentId, String departmentName, String documentTitle,
                                      String status) {
    public ArchiveAccessDetailItem(Long id, Long applicationId, Long versionId, Long employeeId,
                                   Long departmentId, String status) {
        this(id, applicationId, versionId, 0, employeeId, null, null, departmentId, null, null, status);
    }
}
