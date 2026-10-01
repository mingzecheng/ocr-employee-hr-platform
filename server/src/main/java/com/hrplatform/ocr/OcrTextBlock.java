package com.hrplatform.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrTextBlock(
        String text,
        double confidence,
        List<Integer> bbox,
        @JsonProperty("pageNo") int pageNo
) {
}
