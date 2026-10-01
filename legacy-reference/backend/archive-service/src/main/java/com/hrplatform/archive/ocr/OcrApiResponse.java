package com.hrplatform.archive.ocr;

public record OcrApiResponse<T>(Object code, String message, T data) {
    public boolean success() {
        return code instanceof Number number && number.intValue() == 0
                || "0".equals(String.valueOf(code));
    }
}
