package com.hrplatform.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrTaskData(
        String taskId,
        String status,
        String engineVersion,
        @JsonProperty("processingDurationMs") long processingDurationMs,
        List<OcrTextBlock> textBlocks,
        List<OcrRawField> fields,
        String errorMessage,
        @JsonProperty("detectionPreview") OcrPreview detectionPreview
) {
    public OcrTaskData(String taskId, String status, String engineVersion,
                       long processingDurationMs, List<OcrTextBlock> textBlocks,
                       List<OcrRawField> fields, String errorMessage) {
        this(taskId, status, engineVersion, processingDurationMs, textBlocks, fields, errorMessage, null);
    }
}
