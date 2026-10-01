package com.hrplatform.workflow.todo;

import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.api.TraceId;
import com.hrplatform.workflow.authorization.WorkflowDataScopeDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = TodoController.class)
public class TodoExceptionHandler {
    @ExceptionHandler(WorkflowDataScopeDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(WorkflowDataScopeDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    @ExceptionHandler(TodoArchiveUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> unavailable(TodoArchiveUnavailableException exception) {
        return response(HttpStatus.BAD_GATEWAY, "TODO_SOURCE_UNAVAILABLE", "Todo source is unavailable");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message, TraceId.current()));
    }
}
