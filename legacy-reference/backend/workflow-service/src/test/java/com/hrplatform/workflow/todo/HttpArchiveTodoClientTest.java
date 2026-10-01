package com.hrplatform.workflow.todo;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpArchiveTodoClientTest {

    @Test
    void nonSuccessArchiveEnvelopeIsUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpArchiveTodoClient client = new HttpArchiveTodoClient(builder.baseUrl("http://archive.test").build());
        server.expect(requestTo("http://archive.test/internal/archive/ocr-failures?limit=10"))
                .andRespond(withSuccess("{\"code\":\"ARCHIVE_ERROR\",\"message\":\"failed\",\"data\":null}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.listFailedOcr("token", 10))
                .isInstanceOf(TodoArchiveUnavailableException.class);

        server.verify();
    }
}
