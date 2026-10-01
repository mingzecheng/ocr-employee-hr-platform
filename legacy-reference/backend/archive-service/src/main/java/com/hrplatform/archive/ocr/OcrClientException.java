package com.hrplatform.archive.ocr;

public class OcrClientException extends RuntimeException {
    public OcrClientException(String message) {
        super(message);
    }

    public OcrClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
