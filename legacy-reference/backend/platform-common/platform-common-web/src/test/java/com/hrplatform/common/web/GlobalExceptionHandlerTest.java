package com.hrplatform.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentMapsToValidationErrorWithoutStackTrace() {
        ResponseEntity<?> response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOfSatisfying(
                com.hrplatform.common.api.ApiResponse.class,
                body -> {
                    assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
                    assertThat(body.message()).isEqualTo("bad input");
                    assertThat(body.toString()).doesNotContain("at ");
                });
    }

    @Test
    void unknownExceptionMapsToStableInternalError() {
        ResponseEntity<?> response = handler.handleUnknown(new IllegalStateException("database password"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isInstanceOfSatisfying(
                com.hrplatform.common.api.ApiResponse.class,
                body -> {
                    assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
                    assertThat(body.message()).isEqualTo("Internal server error");
                    assertThat(body.toString()).doesNotContain("database password");
                });
    }
}
