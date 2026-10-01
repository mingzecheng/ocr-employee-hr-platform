package com.hrplatform.archive.api;

import com.hrplatform.archive.ocr.OcrClientException;
import com.hrplatform.archive.ocr.OcrPreviewMissingException;
import com.hrplatform.archive.storage.StorageException;
import com.hrplatform.common.api.ApiResponse;
import com.hrplatform.common.api.TraceId;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ArchiveExceptionHandler {
    @ExceptionHandler(ArchiveNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ArchiveNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "ARCHIVE_NOT_FOUND", "Archive resource not found");
    }

    @ExceptionHandler(DataScopeDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> dataScopeDenied(DataScopeDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    @ExceptionHandler(OcrPreviewMissingException.class)
    public ResponseEntity<ApiResponse<Void>> previewMissing(OcrPreviewMissingException exception) {
        return response(HttpStatus.NOT_FOUND, "DETECTION_PREVIEW_NOT_FOUND",
                "OCR detection preview not found");
    }

    @ExceptionHandler(EmployeeExistsException.class)
    public ResponseEntity<ApiResponse<Void>> employeeExists(EmployeeExistsException exception) {
        return response(HttpStatus.CONFLICT, "EMPLOYEE_ALREADY_EXISTS", "Employee number already exists");
    }

    @ExceptionHandler(InvalidImageException.class)
    public ResponseEntity<ApiResponse<Void>> invalidImage(InvalidImageException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", exception.getMessage());
    }

    @ExceptionHandler(OcrClientException.class)
    public ResponseEntity<ApiResponse<Void>> ocrClient(OcrClientException exception) {
        return response(HttpStatus.BAD_GATEWAY, "OCR_SERVICE_ERROR", "OCR service request failed");
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Void>> storage(StorageException exception) {
        return response(HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Archive file storage is unavailable");
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message, TraceId.current()));
    }
}
