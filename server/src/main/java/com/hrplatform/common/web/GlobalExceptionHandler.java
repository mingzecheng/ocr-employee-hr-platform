package com.hrplatform.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.hrplatform.auth.InvalidCredentialsException;
import com.hrplatform.employee.DataScopeDeniedException;
import com.hrplatform.archive.InvalidArchiveFileException;
import com.hrplatform.ocr.OcrBindingNotFoundException;
import com.hrplatform.ocr.OcrFieldNotFoundException;
import com.hrplatform.ocr.OcrPreviewMissingException;
import com.hrplatform.workflow.RequestStateException;
import com.hrplatform.access.ArchiveAccessStateException;
import com.hrplatform.inventory.InventoryStateException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", message, traceId(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> illegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> invalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("UNAUTHORIZED", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler({DataScopeDeniedException.class, OcrBindingNotFoundException.class,
            OcrFieldNotFoundException.class, OcrPreviewMissingException.class})
    public ResponseEntity<ApiResponse<Void>> notFoundOrDenied(RuntimeException exception, HttpServletRequest request) {
        String code = exception instanceof DataScopeDeniedException ? "FORBIDDEN" : "NOT_FOUND";
        HttpStatus status = exception instanceof DataScopeDeniedException ? HttpStatus.FORBIDDEN : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(ApiResponse.failure(code, exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(InvalidArchiveFileException.class)
    public ResponseEntity<ApiResponse<Void>> invalidArchive(InvalidArchiveFileException exception,
                                                            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(RequestStateException.class)
    public ResponseEntity<ApiResponse<Void>> requestState(RequestStateException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("STATE_CONFLICT", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler({ArchiveAccessStateException.class, InventoryStateException.class})
    public ResponseEntity<ApiResponse<Void>> resourceState(RuntimeException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("STATE_CONFLICT", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.internalServerError()
                .body(ApiResponse.failure("INTERNAL_ERROR", "服务暂时不可用", traceId(request)));
    }

    public HttpStatus statusFor(String code) {
        return switch (code) {
            case "VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST;
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private String traceId(HttpServletRequest request) {
        String value = request.getHeader(TraceIdFilter.HEADER);
        return value == null ? "unknown" : value;
    }
}
