package com.hrplatform.workflow.inventory;

import java.time.LocalDateTime;

public class InventoryItem {
    private Long id;
    private Long taskId;
    private Long archiveDocumentId;
    private Long employeeId;
    private Long departmentId;
    private Long expectedVersionId;
    private Long actualVersionId;
    private String actualStatus;
    private String differenceType;
    private String differenceDescription;
    private Long checkedBy;
    private LocalDateTime checkedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getArchiveDocumentId() { return archiveDocumentId; }
    public void setArchiveDocumentId(Long archiveDocumentId) { this.archiveDocumentId = archiveDocumentId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getExpectedVersionId() { return expectedVersionId; }
    public void setExpectedVersionId(Long expectedVersionId) { this.expectedVersionId = expectedVersionId; }
    public Long getActualVersionId() { return actualVersionId; }
    public void setActualVersionId(Long actualVersionId) { this.actualVersionId = actualVersionId; }
    public String getActualStatus() { return actualStatus; }
    public void setActualStatus(String actualStatus) { this.actualStatus = actualStatus; }
    public String getDifferenceType() { return differenceType; }
    public void setDifferenceType(String differenceType) { this.differenceType = differenceType; }
    public String getDifferenceDescription() { return differenceDescription; }
    public void setDifferenceDescription(String differenceDescription) { this.differenceDescription = differenceDescription; }
    public Long getCheckedBy() { return checkedBy; }
    public void setCheckedBy(Long checkedBy) { this.checkedBy = checkedBy; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
}
