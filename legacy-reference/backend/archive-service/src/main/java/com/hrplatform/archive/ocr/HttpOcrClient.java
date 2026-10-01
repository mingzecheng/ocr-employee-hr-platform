package com.hrplatform.archive.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.MultipartBodyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.nio.charset.StandardCharsets;

public class HttpOcrClient implements OcrClient {
    private static final Logger log = LoggerFactory.getLogger(HttpOcrClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ParameterizedTypeReference<OcrApiResponse<OcrTaskData>> TASK_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String internalToken;

    public HttpOcrClient(RestClient restClient) {
        this(restClient, "");
    }

    public HttpOcrClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    @Override
    public OcrTaskData createTask(OcrSourceData source, String documentType) {
        MultipartBodyBuilder form = new MultipartBodyBuilder();
        form.part("sourceObjectKey", source.objectKey());
        form.part("originalName", source.originalName());
        form.part("contentType", source.contentType());
        form.part("size", Long.toString(source.size()));
        form.part("sha256", source.sha256() == null ? "" : source.sha256());
        if (documentType != null && !documentType.isBlank()) {
            form.part("documentType", documentType);
        }
        var requestBuilder = restClient.post()
                .uri("/api/ocr/tasks")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("X-OCR-Internal-Token", internalToken)
                .body(form.build());
        OcrApiResponse<OcrTaskData> response = requestBuilder.retrieve()
                .onStatus(HttpStatusCode::isError, (request, result) -> {
                    log.warn("OCR task request failed status={} body={}", result.getStatusCode().value(),
                            responseBody(result));
                    throw new OcrClientException("OCR task request failed with HTTP "
                            + result.getStatusCode().value());
                })
                .body(TASK_RESPONSE);
        return requireTask(response, "create task");
    }

    @Override
    public OcrTaskData createTask(String filename, String contentType, byte[] content, String documentType) {
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultipartBodyBuilder form = new MultipartBodyBuilder();
        form.part("file", resource).filename(filename).contentType(MediaType.parseMediaType(contentType));
        if (documentType != null && !documentType.isBlank()) {
            form.part("documentType", documentType);
        }
        OcrApiResponse<OcrTaskData> response = restClient.post()
                .uri("/api/ocr/tasks")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form.build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, result) -> {
                    throw new OcrClientException("OCR task request failed with HTTP "
                            + result.getStatusCode().value());
                })
                .body(TASK_RESPONSE);
        return requireTask(response, "create task");
    }

    @Override
    public OcrTaskData getTask(String taskId) {
        OcrApiResponse<OcrTaskData> response = restClient.get()
                .uri("/api/ocr/tasks/{taskId}", taskId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, result) -> {
                    throw new OcrClientException("OCR task query failed with HTTP "
                            + result.getStatusCode().value());
                })
                .body(TASK_RESPONSE);
        return requireTask(response, "query task");
    }

    @Override
    public byte[] getDetectionPreview(String taskId) {
        return restClient.get()
                .uri("/api/ocr/tasks/{taskId}/detection-preview", taskId)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        (request, result) -> {
                            String code = responseCode(result);
                            if ("DETECTION_PREVIEW_NOT_FOUND".equals(code)) {
                                throw new OcrPreviewMissingException(taskId);
                            }
                            throw new OcrClientException("OCR preview request failed with HTTP 404"
                                    + (code == null ? "" : " (" + code + ")"));
                        })
                .onStatus(HttpStatusCode::isError, (request, result) -> {
                    throw new OcrClientException("OCR preview request failed with HTTP "
                            + result.getStatusCode().value());
                })
                .body(byte[].class);
    }

    @Override
    public OcrTaskData correctField(String taskId, String fieldCode, String value,
                                    String operatorId, String reason) {
        Map<String, Object> body = Map.of(
                "value", value,
                "operatorId", operatorId,
                "reason", reason == null ? "" : reason);
        OcrApiResponse<OcrTaskData> response = restClient.put()
                .uri("/api/ocr/tasks/{taskId}/fields/{fieldCode}", taskId, fieldCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, result) -> {
                    throw new OcrClientException("OCR field correction failed with HTTP "
                            + result.getStatusCode().value());
                })
                .body(TASK_RESPONSE);
        return requireTask(response, "correct field");
    }

    private OcrTaskData requireTask(OcrApiResponse<OcrTaskData> response, String operation) {
        if (response == null || !response.success() || response.data() == null) {
            throw new OcrClientException("OCR " + operation + " returned an unsuccessful response");
        }
        return response.data();
    }

    private String responseBody(ClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return "<unavailable>";
        }
    }

    private String responseCode(ClientHttpResponse response) {
        try {
            JsonNode body = JSON.readTree(response.getBody());
            return body == null || body.get("code") == null ? null : body.get("code").asText();
        } catch (Exception exception) {
            return null;
        }
    }
}
