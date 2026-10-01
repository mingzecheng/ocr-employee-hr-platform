package com.hrplatform.workflow.access;

import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.api.TraceId;
import com.hrplatform.workflow.authorization.WorkflowAuthorizationException;
import com.hrplatform.workflow.authorization.WorkflowDataScopeDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ArchiveAccessExceptionHandler {
    @ExceptionHandler(ArchiveAccessNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ArchiveAccessNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "ARCHIVE_ACCESS_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ArchiveAccessStateException.class)
    public ResponseEntity<ApiResponse<Void>> state(ArchiveAccessStateException exception) {
        return response(HttpStatus.CONFLICT, "STATE_NOT_ALLOWED", exception.getMessage());
    }

    @ExceptionHandler(WorkflowDataScopeDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(WorkflowDataScopeDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    @ExceptionHandler(WorkflowAuthorizationException.class)
    public ResponseEntity<ApiResponse<Void>> authorizationUnavailable(WorkflowAuthorizationException exception) {
        return response(HttpStatus.BAD_GATEWAY, "ARCHIVE_AUTHORIZATION_UNAVAILABLE",
                "Archive authorization service is unavailable");
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message, TraceId.current()));
    }
}
