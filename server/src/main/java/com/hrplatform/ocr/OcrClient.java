package com.hrplatform.ocr;

public interface OcrClient {
    OcrTaskData create(OcrCreateRequest request);

    OcrTaskData getTask(String taskId);

    byte[] getPreview(String taskId);

    default OcrTaskData correctField(String taskId, String fieldCode, String value,
                                     String operatorId, String reason) {
        throw new OcrClientException("OCR 字段修订接口不可用");
    }
}
