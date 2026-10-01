package com.hrplatform.archive.document;

import java.time.LocalDateTime;

public class ArchiveVersion {
    private Long id;
    private Long documentId;
    private int versionNo;
    private Long fileObjectId;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private FileObject fileObject;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public Long getFileObjectId() { return fileObjectId; }
    public void setFileObjectId(Long fileObjectId) { this.fileObjectId = fileObjectId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public FileObject getFileObject() { return fileObject; }
    public void setFileObject(FileObject fileObject) { this.fileObject = fileObject; }
}
