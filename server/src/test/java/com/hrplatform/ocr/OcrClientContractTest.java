package com.hrplatform.ocr;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OcrClientContractTest {
    @Test
    void createTaskSendsInternalTokenAndSourceReference() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ocr.test/api/ocr/tasks"))
                .andExpect(header("X-OCR-Internal-Token", "token"))
                .andRespond(withSuccess("""
                        {"code":0,"message":"OK","data":{"taskId":"task-3","status":"SUCCEEDED","engineVersion":"paddle-3","processingDurationMs":10,"textBlocks":[],"fields":[]}}
                        """, MediaType.APPLICATION_JSON));

        OcrTaskData result = new HttpOcrClient(builder.baseUrl("http://ocr.test").build(), "token")
                .create(new OcrCreateRequest("archive/1/a.png", "a.png", "image/png", 12L, "hash", "employee_profile"));

        assertThat(result.taskId()).isEqualTo("task-3");
        server.verify();
    }

    @Test
    void httpFailureIsMappedToStableOcrClientException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ocr.test/api/ocr/tasks/missing"))
                .andRespond(withStatus(NOT_FOUND).body("""
                        {"code":"TASK_NOT_FOUND","message":"OCR task not found","data":null}
                        """).contentType(MediaType.APPLICATION_JSON));

        HttpOcrClient client = new HttpOcrClient(builder.baseUrl("http://ocr.test").build(), "token");

        assertThatThrownBy(() -> client.getTask("missing"))
                .isInstanceOf(OcrClientException.class)
                .hasMessageContaining("TASK_NOT_FOUND");
        server.verify();
    }
}
