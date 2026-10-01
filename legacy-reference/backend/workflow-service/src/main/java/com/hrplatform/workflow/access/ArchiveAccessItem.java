package com.hrplatform.workflow.access;

public class ArchiveAccessItem {
    private Long id;
    private Long applicationId;
    private Long archiveDocumentId;
    private Long archiveVersionId;
    private String scope;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getArchiveDocumentId() { return archiveDocumentId; }
    public void setArchiveDocumentId(Long archiveDocumentId) { this.archiveDocumentId = archiveDocumentId; }
    public Long getArchiveVersionId() { return archiveVersionId; }
    public void setArchiveVersionId(Long archiveVersionId) { this.archiveVersionId = archiveVersionId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
}
