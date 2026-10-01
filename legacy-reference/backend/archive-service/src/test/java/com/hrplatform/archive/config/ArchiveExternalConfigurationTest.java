package com.hrplatform.archive.config;

import com.hrplatform.archive.ocr.OcrClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveExternalConfigurationTest {

    @Test
    void ocrClientUsesHttp11ForCleartextMultipartRequests() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> upgradeHeader = new AtomicReference<>();
        server.createContext("/api/ocr/tasks", exchange -> {
            upgradeHeader.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            exchange.getRequestBody().readAllBytes();
            byte[] response = """
                    {"code":0,"message":"OK","data":{
                      "taskId":"task-1","status":"SUCCEEDED","engineVersion":"test",
                      "sourceFile":{"originalName":"profile.png"},"textBlocks":[],"fields":[],
                      "detectionPreview":null
                    }}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            OcrClient client = new ArchiveExternalConfiguration()
                    .ocrClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());

            client.createTask("profile.png", "image/png", new byte[]{1, 2, 3}, "employee_profile");

            assertThat(upgradeHeader.get()).isNull();
        } finally {
            server.stop(0);
        }
    }
}
