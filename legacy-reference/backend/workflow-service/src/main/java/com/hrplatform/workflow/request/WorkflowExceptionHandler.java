package com.hrplatform.workflow.request;

import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.api.TraceId;
import com.hrplatform.workflow.authorization.WorkflowAuthorizationException;
import com.hrplatform.workflow.authorization.WorkflowDataScopeDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WorkflowExceptionHandler {
    @ExceptionHandler(RequestNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(RequestNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(RequestStateException.class)
    public ResponseEntity<ApiResponse<Void>> state(RequestStateException exception) {
        return response(HttpStatus.CONFLICT, "STATE_NOT_ALLOWED", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
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
