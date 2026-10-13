package com.hrplatform.ocr;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OcrClientContractTest {
    @Test
    void jdkHttpClientEncodesSourceObjectReferenceAsMultipartFields() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> contentLength = new AtomicReference<>();
        AtomicReference<String> transferEncoding = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/api/ocr/tasks", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"code":0,"message":"OK","data":{"taskId":"task-4","status":"SUCCEEDED","engineVersion":"paddle-3","processingDurationMs":10,"textBlocks":[],"fields":[]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpOcrClient client = HttpOcrClient.create(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "token");
            client.create(new OcrCreateRequest(
                    "archive/4/profile.png", "profile.png", "image/png", 123L, "abc123", "employee_profile"));

            assertThat(contentType.get()).contains("multipart/form-data", "boundary=");
            assertThat(contentLength.get()).isNotBlank();
            assertThat(transferEncoding.get()).isNull();
            assertThat(requestBody.get())
                    .contains("name=\"sourceObjectKey\"", "archive/4/profile.png")
                    .contains("name=\"originalName\"", "profile.png")
                    .contains("name=\"contentType\"", "image/png")
                    .contains("name=\"size\"", "123")
                    .contains("name=\"sha256\"", "abc123")
                    .contains("name=\"documentType\"", "employee_profile");
        } finally {
            server.stop(0);
        }
    }

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
