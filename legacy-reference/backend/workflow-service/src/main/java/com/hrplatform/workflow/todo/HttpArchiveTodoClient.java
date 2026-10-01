package com.hrplatform.workflow.todo;

import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.workflow.authorization.WorkflowDataScopeDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class HttpArchiveTodoClient implements ArchiveTodoClient {
    private static final ParameterizedTypeReference<ApiResponse<List<TodoDtos.OcrFailureItem>>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};
    private final RestClient client;

    HttpArchiveTodoClient(RestClient client) {
        this.client = client;
    }

    @Autowired
    public HttpArchiveTodoClient(RestClient.Builder builder,
                                 @Value("${workflow.archive-service-url}") String baseUrl,
                                 @Value("${workflow.todo.archive-connect-timeout:2s}") Duration connectTimeout,
                                 @Value("${workflow.todo.archive-read-timeout:5s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.requestFactory(requestFactory).baseUrl(baseUrl).build();
    }

    @Override
    public List<TodoDtos.OcrFailureItem> listFailedOcr(String bearerToken, int limit) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new WorkflowDataScopeDeniedException();
        }
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        try {
            ApiResponse<List<TodoDtos.OcrFailureItem>> response = client.get()
                    .uri(builder -> builder.path("/internal/archive/ocr-failures")
                            .queryParam("limit", boundedLimit).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve().body(RESPONSE_TYPE);
            if (response == null || !"0".equals(response.code()) || response.data() == null) {
                throw new TodoArchiveUnavailableException("Archive todo response was unsuccessful");
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403 || status == 404) {
                throw new WorkflowDataScopeDeniedException();
            }
            throw new TodoArchiveUnavailableException("Archive todo request failed", exception);
        } catch (RuntimeException exception) {
            throw new TodoArchiveUnavailableException("Archive todo request failed", exception);
        }
    }
}
