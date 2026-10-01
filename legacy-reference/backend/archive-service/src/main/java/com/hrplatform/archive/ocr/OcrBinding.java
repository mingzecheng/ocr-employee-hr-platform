package com.hrplatform.archive.ocr;

import java.time.LocalDateTime;

public class OcrBinding {
    private Long id;
    private Long versionId;
    private String ocrTaskId;
    private String status;
    private String engineVersion;
    private String detectionPreviewUrl;
    private String detectionPreviewContentType;
    private Long detectionPreviewSize;
    private String detectionPreviewSha256;
    private Integer detectionPreviewWidth;
    private Integer detectionPreviewHeight;
    private int fieldCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getOcrTaskId() { return ocrTaskId; }
    public void setOcrTaskId(String ocrTaskId) { this.ocrTaskId = ocrTaskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public String getDetectionPreviewUrl() { return detectionPreviewUrl; }
    public void setDetectionPreviewUrl(String detectionPreviewUrl) { this.detectionPreviewUrl = detectionPreviewUrl; }
    public String getDetectionPreviewContentType() { return detectionPreviewContentType; }
    public void setDetectionPreviewContentType(String value) { this.detectionPreviewContentType = value; }
    public Long getDetectionPreviewSize() { return detectionPreviewSize; }
    public void setDetectionPreviewSize(Long value) { this.detectionPreviewSize = value; }
    public String getDetectionPreviewSha256() { return detectionPreviewSha256; }
    public void setDetectionPreviewSha256(String value) { this.detectionPreviewSha256 = value; }
    public Integer getDetectionPreviewWidth() { return detectionPreviewWidth; }
    public void setDetectionPreviewWidth(Integer value) { this.detectionPreviewWidth = value; }
    public Integer getDetectionPreviewHeight() { return detectionPreviewHeight; }
    public void setDetectionPreviewHeight(Integer value) { this.detectionPreviewHeight = value; }
    public int getFieldCount() { return fieldCount; }
    public void setFieldCount(int fieldCount) { this.fieldCount = fieldCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
