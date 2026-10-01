package com.hrplatform.archive.ocr;

import java.time.LocalDateTime;

public class OcrFieldConfirmation {
    private Long id;
    private Long bindingId;
    private String fieldCode;
    private String originalValue;
    private String confirmedValue;
    private Double confidence;
    private Long operatorId;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBindingId() { return bindingId; }
    public void setBindingId(Long bindingId) { this.bindingId = bindingId; }
    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public String getOriginalValue() { return originalValue; }
    public void setOriginalValue(String value) { this.originalValue = value; }
    public String getConfirmedValue() { return confirmedValue; }
    public void setConfirmedValue(String value) { this.confirmedValue = value; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
