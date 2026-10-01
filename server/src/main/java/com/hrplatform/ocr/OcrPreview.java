package com.hrplatform.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrPreview(String url, @JsonProperty("contentType") String contentType,
                         long size, String sha256, int width, int height) {
}
