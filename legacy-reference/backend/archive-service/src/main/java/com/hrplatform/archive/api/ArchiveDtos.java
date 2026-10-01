package com.hrplatform.archive.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ArchiveDtos {
    private ArchiveDtos() {
    }

    public record EmployeeCreateRequest(
            @NotBlank @Size(max = 64) String employeeNo,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 64) String idCardNo,
            Long departmentId) {
    }

    public record EmployeeData(Long id, String employeeNo, String name, String idCardNo,
                               Long departmentId, String status) {
    }

    public record EmployeePageData(List<EmployeeData> items, long total, int page, int pageSize) {
    }

    public record ResourceAuthorizationData(Long employeeId, Long departmentId, Long documentId) {
        public ResourceAuthorizationData(Long employeeId, Long departmentId) {
            this(employeeId, departmentId, null);
        }
    }

    public record DocumentUploadData(Long employeeId, Long documentId, Long versionId,
                                     String objectKey, String sha256, long size, String status) {
    }

    public record DocumentData(Long id, Long employeeId, String documentType, String title,
                               String status, Long latestVersionId, Integer latestVersionNo,
                               String latestFileName, String latestContentType, Long latestSize,
                               LocalDateTime createdAt) {
    }

    public record VersionData(Long id, Long documentId, Integer versionNo, String status,
                              String originalName, String contentType, Long sizeBytes,
                              String sha256, Long createdBy, LocalDateTime createdAt) {
    }

    public record DownloadData(String fileName, String contentType, byte[] content) {
    }

    public record FieldCorrectionRequest(
            @NotBlank @Size(max = 512) String value,
            @Size(max = 512) String reason) {
    }

    public record OcrResultData(Long bindingId, Long versionId, String taskId, String status,
                                String engineVersion, JsonNode sourceFile, JsonNode detectionPreview,
                                JsonNode textBlocks, JsonNode fields, String errorMessage) {
    }
}
