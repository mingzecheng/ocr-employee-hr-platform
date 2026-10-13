package com.hrplatform.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RequestCreateRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .registerModule(new RequestCreateRequestModule());
    @Autowired
    private ObjectMapper springObjectMapper;

    @Test
    void springObjectMapperUsesTheStrictRequestModule() {
        String json = "{\"requestType\":\"LEAVE\",\"employeeId\":7,\"payloadJson\":\"{}\"}";

        assertThatThrownBy(() -> springObjectMapper.readValue(json, RequestCreateRequest.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void mapsRequestTypeToItsDedicatedDto() throws Exception {
        String json = "{\"requestType\":\"LEAVE\",\"employeeId\":7,\"leaveType\":\"ANNUAL\","
                + "\"leaveStartDate\":\"2026-10-12\",\"leaveEndDate\":\"2026-10-13\",\"reason\":\"休假\"}";

        RequestCreateRequest request = objectMapper.readValue(json, RequestCreateRequest.class);

        assertThat(request).isInstanceOf(RequestCreateRequest.Leave.class);
        assertThat(request.requestType()).isEqualTo("LEAVE");
    }

    @Test
    void rejectsOpaquePayloadJson() {
        String json = "{\"requestType\":\"LEAVE\",\"employeeId\":7,\"payloadJson\":\"{}\"}";

        assertThatThrownBy(() -> objectMapper.readValue(json, RequestCreateRequest.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsFieldsThatDoNotBelongToTheSelectedRequestType() {
        String json = "{\"requestType\":\"LEAVE\",\"employeeId\":7,\"targetDepartmentId\":9}";

        assertThatThrownBy(() -> objectMapper.readValue(json, RequestCreateRequest.class))
                .isInstanceOf(JsonProcessingException.class);
    }
}
