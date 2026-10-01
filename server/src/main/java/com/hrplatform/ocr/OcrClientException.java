package com.hrplatform.ocr;

public class OcrClientException extends RuntimeException {
    public OcrClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public OcrClientException(String message) {
        super(message);
    }
}
