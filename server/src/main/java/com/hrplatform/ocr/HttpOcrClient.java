package com.hrplatform.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

public class HttpOcrClient implements OcrClient {
    private final RestClient restClient;
    private final String internalToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpOcrClient(RestClient restClient, String internalToken) {
        this.restClient = restClient;
        this.internalToken = internalToken;
    }

    public static HttpOcrClient create(String baseUrl, String internalToken) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new HttpOcrClient(RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build(), internalToken);
    }

    @Override
    public OcrTaskData create(OcrCreateRequest request) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("sourceObjectKey", request.sourceObjectKey());
        form.add("originalName", request.originalName());
        form.add("contentType", request.contentType());
        form.add("size", request.size());
        form.add("sha256", request.sha256());
        if (request.documentType() != null) {
            form.add("documentType", request.documentType());
        }
        try {
            return parse(restClient.post()
                    .uri("/api/ocr/tasks")
                    .header("X-OCR-Internal-Token", internalToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class));
        } catch (RestClientResponseException exception) {
            throw responseException("创建 OCR 任务", exception);
        } catch (OcrClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OcrClientException("创建 OCR 任务网络异常", exception);
        }
    }

    @Override
    public OcrTaskData getTask(String taskId) {
        try {
            return parse(restClient.get().uri("/api/ocr/tasks/{taskId}", taskId)
                    .header("X-OCR-Internal-Token", internalToken).retrieve().body(String.class));
        } catch (RestClientResponseException exception) {
            throw responseException("查询 OCR 任务", exception);
        } catch (OcrClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OcrClientException("查询 OCR 任务网络异常", exception);
        }
    }

    @Override
    public byte[] getPreview(String taskId) {
        try {
            return restClient.get().uri("/api/ocr/tasks/{taskId}/detection-preview", taskId)
                    .header("X-OCR-Internal-Token", internalToken).retrieve().body(byte[].class);
        } catch (RestClientResponseException exception) {
            OcrClientException mapped = responseException("获取 OCR 预览", exception);
            if (exception.getStatusCode().value() == 404
                    && mapped.getMessage().contains("DETECTION_PREVIEW_NOT_FOUND")) {
                throw new OcrPreviewMissingException(taskId);
            }
            throw mapped;
        } catch (RuntimeException exception) {
            if (exception instanceof OcrClientException ocrException) {
                throw ocrException;
            }
            throw new OcrClientException("OCR 预览获取失败", exception);
        }
    }

    @Override
    public OcrTaskData correctField(String taskId, String fieldCode, String value,
                                    String operatorId, String reason) {
        try {
            return parse(restClient.put()
                    .uri("/api/ocr/tasks/{taskId}/fields/{fieldCode}", taskId, fieldCode)
                    .header("X-OCR-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("value", value, "operatorId", operatorId,
                            "reason", reason == null ? "" : reason))
                    .retrieve().body(String.class));
        } catch (RestClientResponseException exception) {
            throw responseException("修订 OCR 字段", exception);
        } catch (OcrClientException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OcrClientException("修订 OCR 字段网络异常", exception);
        }
    }

    private OcrClientException responseException(String operation, RestClientResponseException exception) {
        String message = operation + "失败 HTTP " + exception.getStatusCode().value();
        String body = exception.getResponseBodyAsString();
        try {
            JsonNode root = objectMapper.readTree(body);
            String code = root.path("code").asText("");
            String apiMessage = root.path("message").asText("");
            if (!code.isBlank()) {
                message += " [" + code + "]";
            }
            if (!apiMessage.isBlank()) {
                message += " " + apiMessage;
            }
        } catch (Exception ignored) {
            // Keep the stable HTTP operation message when the upstream body is not JSON.
        }
        return new OcrClientException(message, exception);
    }

    private OcrTaskData parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("code").isValueNode() || !root.path("code").asText().equals("0")) {
                throw new OcrClientException(root.path("message").asText("OCR 返回失败"));
            }
            return objectMapper.treeToValue(root.path("data"), OcrTaskData.class);
        } catch (OcrClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OcrClientException("OCR 响应解析失败", exception);
        }
    }
}
