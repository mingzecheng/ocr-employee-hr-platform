package com.hrplatform.workflow.authorization;

import com.hrplatform.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpWorkflowAuthorizationClient implements WorkflowAuthorizationClient {
    private static final ParameterizedTypeReference<ApiResponse<AuthorizationData>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient client;

    public HttpWorkflowAuthorizationClient(RestClient.Builder builder,
                                           @Value("${workflow.archive-service-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public AuthorizedResource authorizeEmployee(long employeeId, String bearerToken) {
        return get("/internal/archive/employees/" + employeeId + "/authorization", bearerToken);
    }

    @Override
    public AuthorizedResource authorizeDocument(long documentId, String bearerToken) {
        return get("/internal/archive/documents/" + documentId + "/authorization", bearerToken);
    }

    @Override
    public AuthorizedResource authorizeVersion(long versionId, String bearerToken) {
        return get("/internal/archive/versions/" + versionId + "/authorization", bearerToken);
    }

    private AuthorizedResource get(String path, String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new WorkflowDataScopeDeniedException();
        }
        try {
            ApiResponse<AuthorizationData> response = client.get().uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(RESPONSE_TYPE);
            if (response == null || !"0".equals(response.code()) || response.data() == null) {
                throw new WorkflowDataScopeDeniedException();
            }
            AuthorizationData data = response.data();
            return new AuthorizedResource(data.employeeId(), data.departmentId(), data.documentId());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403
                    || exception.getStatusCode().value() == 404) {
                throw new WorkflowDataScopeDeniedException();
            }
            throw new WorkflowAuthorizationException(exception);
        } catch (WorkflowDataScopeDeniedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WorkflowAuthorizationException(exception);
        }
    }

    private record AuthorizationData(Long employeeId, Long departmentId, Long documentId) {
    }
}
