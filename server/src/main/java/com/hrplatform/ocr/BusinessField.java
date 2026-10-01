package com.hrplatform.ocr;

import java.util.List;

public record BusinessField(
        String fieldCode,
        String value,
        double confidence,
        List<Integer> bbox,
        int pageNo,
        String validationStatus,
        boolean reviewRequired,
        String validationMessage
) {
}
