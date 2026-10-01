package com.hrplatform.archive.ocr;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

class HttpOcrClientTest {

    @Test
    void archiveOcrUsesObjectReferenceMultipartFieldsInsteadOfFileBytes() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ocr.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOcrClient client = new HttpOcrClient(builder.build(), "test-token");
        server.expect(requestTo("http://ocr.test/api/ocr/tasks"))
                .andExpect(content().string(allOf(
                        containsString("name=\"sourceObjectKey\""),
                        containsString("archive/employee/1/a.png"),
                        containsString("name=\"originalName\""),
                        containsString("name=\"contentType\""),
                        containsString("name=\"size\""),
                        containsString("name=\"sha256\""),
                        not(containsString("name=\"file\"")))))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":0,\"message\":\"OK\",\"data\":{\"taskId\":\"task-1\",\"status\":\"SUCCEEDED\"}}"));

        client.createTask(new OcrSourceData("archive/employee/1/a.png", "a.png",
                "image/png", 3, "a".repeat(64)), "employee_profile");

        server.verify();
    }

    @Test
    void taskNotFoundIsNotReportedAsMissingPreview() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ocr.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOcrClient client = new HttpOcrClient(builder.build());
        server.expect(requestTo("http://ocr.test/api/ocr/tasks/missing/detection-preview"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"TASK_NOT_FOUND\",\"message\":\"OCR task not found\"}"));

        assertThatThrownBy(() -> client.getDetectionPreview("missing"))
                .isInstanceOf(OcrClientException.class)
                .isNotInstanceOf(OcrPreviewMissingException.class);

        server.verify();
    }

    @Test
    void detectionPreviewNotFoundKeepsPreviewSpecificException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ocr.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOcrClient client = new HttpOcrClient(builder.build());
        server.expect(requestTo("http://ocr.test/api/ocr/tasks/task-1/detection-preview"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"DETECTION_PREVIEW_NOT_FOUND\",\"message\":\"missing\"}"));

        assertThatThrownBy(() -> client.getDetectionPreview("task-1"))
                .isInstanceOf(OcrPreviewMissingException.class);

        server.verify();
    }
}
