package com.hrplatform.archive.ocr;

import com.fasterxml.jackson.databind.JsonNode;

public class OcrTaskData {
    private String taskId;
    private String status;
    private String engineVersion;
    private JsonNode sourceFile;
    private JsonNode detectionPreview;
    private JsonNode textBlocks;
    private JsonNode fields;
    private String errorMessage;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public JsonNode getSourceFile() { return sourceFile; }
    public void setSourceFile(JsonNode sourceFile) { this.sourceFile = sourceFile; }
    public JsonNode getDetectionPreview() { return detectionPreview; }
    public void setDetectionPreview(JsonNode detectionPreview) { this.detectionPreview = detectionPreview; }
    public JsonNode getTextBlocks() { return textBlocks; }
    public void setTextBlocks(JsonNode textBlocks) { this.textBlocks = textBlocks; }
    public JsonNode getFields() { return fields; }
    public void setFields(JsonNode fields) { this.fields = fields; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
