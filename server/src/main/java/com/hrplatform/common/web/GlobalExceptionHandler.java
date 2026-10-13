package com.hrplatform.common.web;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.log.PiiMasker;
import com.hrplatform.common.security.JwtPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.util.StringUtils;
import com.hrplatform.auth.InvalidCredentialsException;
import com.hrplatform.common.security.InvalidRefreshTokenException;
import com.hrplatform.common.security.RefreshTokenUnavailableException;
import com.hrplatform.employee.DataScopeDeniedException;
import com.hrplatform.archive.InvalidArchiveFileException;
import com.hrplatform.archive.ArchiveReadDeniedException;
import com.hrplatform.ocr.OcrBindingNotFoundException;
import com.hrplatform.ocr.OcrFieldNotFoundException;
import com.hrplatform.ocr.OcrPreviewMissingException;
import com.hrplatform.workflow.RequestStateException;
import com.hrplatform.common.idempotency.IdempotentReplayException;
import com.hrplatform.access.ArchiveAccessStateException;
import com.hrplatform.audit.OperationLogNotFoundException;
import com.hrplatform.inventory.InventoryStateException;
import com.hrplatform.organization.OrganizationStateException;
import com.hrplatform.organization.OrganizationDeletionException;
import com.hrplatform.auth.AccountAdminStateException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final OperationLogService operationLogService;

    public GlobalExceptionHandler() {
        this(null);
    }

    @Autowired
    public GlobalExceptionHandler(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", message, traceId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> unreadableBody(HttpMessageNotReadableException exception,
                                                            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR",
                "请求内容格式无效或包含不支持的字段", traceId(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> illegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(OrganizationDeletionException.class)
    public ResponseEntity<ApiResponse<Void>> deletionConflict(OrganizationDeletionException exception, HttpServletRequest request) {
        recordFailure(request, "DELETE_CONFLICT");
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("DELETE_CONFLICT", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> invalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("UNAUTHORIZED", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> invalidRefreshToken(InvalidRefreshTokenException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("UNAUTHORIZED", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(RefreshTokenUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> refreshUnavailable(RefreshTokenUnavailableException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure("SERVICE_UNAVAILABLE", "登录状态续期服务暂不可用，请重新登录", traceId(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        recordFailure(request, "FORBIDDEN");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("FORBIDDEN", "当前账号无权执行此操作", traceId(request)));
    }

    @ExceptionHandler({DataScopeDeniedException.class, ArchiveReadDeniedException.class, OcrBindingNotFoundException.class,
            OcrFieldNotFoundException.class, OcrPreviewMissingException.class, OperationLogNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> notFoundOrDenied(RuntimeException exception, HttpServletRequest request) {
        boolean denied = exception instanceof DataScopeDeniedException || exception instanceof ArchiveReadDeniedException;
        String code = denied ? "FORBIDDEN" : "NOT_FOUND";
        if (denied) {
            recordFailure(request, code);
        }
        HttpStatus status = denied ? HttpStatus.FORBIDDEN : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(ApiResponse.failure(code, exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(InvalidArchiveFileException.class)
    public ResponseEntity<ApiResponse<Void>> invalidArchive(InvalidArchiveFileException exception,
                                                            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(RequestStateException.class)
    public ResponseEntity<ApiResponse<Void>> requestState(RequestStateException exception, HttpServletRequest request) {
        recordFailure(request, "STATE_CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("STATE_CONFLICT", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler({ArchiveAccessStateException.class, InventoryStateException.class,
            OrganizationStateException.class, AccountAdminStateException.class,
            IdempotentReplayException.class})
    public ResponseEntity<ApiResponse<Void>> resourceState(RuntimeException exception, HttpServletRequest request) {
        recordFailure(request, "STATE_CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("STATE_CONFLICT", exception.getMessage(), traceId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure traceId={} type={} message={}",
                traceId(request), exception.getClass().getName(), PiiMasker.maskText(exception.getMessage()));
        recordFailure(request, "INTERNAL_ERROR");
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
        String value = MDC.get(TraceIdFilter.HEADER);
        if (!StringUtils.hasText(value)) {
            value = request.getHeader(TraceIdFilter.HEADER);
        }
        return StringUtils.hasText(value) ? value : "unknown";
    }

    private void recordFailure(HttpServletRequest request, String code) {
        if (operationLogService == null) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long operatorId = authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal
                ? principal.userId() : null;
        String afterSummary = "method=" + request.getMethod() + ";path=" + request.getRequestURI() + ";code=" + code;
        try {
            operationLogService.record(operatorId, "REQUEST_FAILED", "API_REQUEST", null,
                    "FAILED", null, afterSummary);
        } catch (RuntimeException auditFailure) {
            log.error("Failed to persist request failure audit traceId={} type={} message={}",
                    traceId(request), auditFailure.getClass().getName(), PiiMasker.maskText(auditFailure.getMessage()));
        }
    }
}
