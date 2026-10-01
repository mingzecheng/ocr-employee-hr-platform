package com.hrplatform.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrRawField(String fieldCode, String value, double confidence,
                          List<Integer> bbox, @JsonProperty("pageNo") int pageNo,
                          String validationStatus, boolean reviewRequired,
                          String validationMessage) {
    public OcrRawField(String fieldCode, String value, double confidence) {
        this(fieldCode, value, confidence, List.of(), 1, null, false, null);
    }
}
