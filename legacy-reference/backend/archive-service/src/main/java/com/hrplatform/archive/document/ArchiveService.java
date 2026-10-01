package com.hrplatform.archive.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.archive.api.ArchiveDtos;
import com.hrplatform.archive.api.ArchiveNotFoundException;
import com.hrplatform.archive.api.DataScopeDeniedException;
import com.hrplatform.archive.api.InvalidImageException;
import com.hrplatform.archive.employee.Employee;
import com.hrplatform.archive.employee.EmployeeMapper;
import com.hrplatform.archive.ocr.OcrBinding;
import com.hrplatform.archive.ocr.OcrBindingMapper;
import com.hrplatform.archive.ocr.OcrClient;
import com.hrplatform.archive.ocr.OcrFieldConfirmation;
import com.hrplatform.archive.ocr.OcrTaskData;
import com.hrplatform.archive.ocr.OcrSourceData;
import com.hrplatform.archive.storage.ObjectStorage;
import com.hrplatform.common.cache.CacheKeys;
import com.hrplatform.common.security.DataScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ArchiveService {
    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);
    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";
    private static final String JPG = "image/jpg";

    private final EmployeeMapper employeeMapper;
    private final ArchiveMapper archiveMapper;
    private final OcrBindingMapper bindingMapper;
    private final ObjectStorage objectStorage;
    private final OcrClient ocrClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final long maxFileSizeBytes;

    public ArchiveService(EmployeeMapper employeeMapper, ArchiveMapper archiveMapper,
                          OcrBindingMapper bindingMapper, ObjectStorage objectStorage,
                          OcrClient ocrClient, ObjectMapper objectMapper,
                          StringRedisTemplate redis,
                          @org.springframework.beans.factory.annotation.Value("${archive.max-file-size-bytes}")
                          long maxFileSizeBytes) {
        this.employeeMapper = employeeMapper;
        this.archiveMapper = archiveMapper;
        this.bindingMapper = bindingMapper;
        this.objectStorage = objectStorage;
        this.ocrClient = ocrClient;
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional
    public ArchiveDtos.DocumentUploadData uploadDocument(long employeeId, String documentType,
                                                         String title, MultipartFile file,
                                                         Long createdBy, DataScope scope) {
        Employee employee = employeeMapper.findByIdInScope(employeeId, scope.type().name(),
                scope.employeeId(), scope.departmentId());
        if (employee == null) {
            throw new DataScopeDeniedException();
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!isImageType(contentType)) {
            throw new InvalidImageException("Only PNG and JPEG images are supported");
        }
        if (file.isEmpty() || file.getSize() > maxFileSizeBytes) {
            throw new InvalidImageException("Uploaded image exceeds the configured size limit");
        }
        byte[] content = readAndVerifyImage(file, contentType);
        String objectKey = "employee/" + employeeId + "/" + UUID.randomUUID().toString().replace("-", "")
                + extension(contentType);
        ObjectStorage.StoredObject stored = objectStorage.put(objectKey, contentType, content);

        Long archiveRecordId = archiveMapper.findArchiveRecordIdByEmployeeId(employeeId);
        if (archiveRecordId == null) {
            archiveMapper.insertArchiveRecord(employeeId);
            archiveRecordId = archiveMapper.findArchiveRecordIdByEmployeeId(employeeId);
        }
        if (archiveRecordId == null) {
            throw new IllegalStateException("Archive record was not created");
        }
        FileObject fileObject = new FileObject();
        fileObject.setObjectKey(stored.objectKey());
        fileObject.setOriginalName(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        fileObject.setContentType(contentType);
        fileObject.setSizeBytes(stored.sizeBytes());
        fileObject.setSha256(stored.sha256());
        fileObject.setStorageProvider("MINIO");
        archiveMapper.insertFileObject(fileObject);

        ArchiveDocument document = new ArchiveDocument();
        document.setArchiveRecordId(archiveRecordId);
        document.setDocumentType(requireDocumentType(documentType));
        document.setTitle(title == null || title.isBlank() ? fileObject.getOriginalName() : title.trim());
        document.setStatus("ACTIVE");
        archiveMapper.insertDocument(document);

        ArchiveVersion version = new ArchiveVersion();
        version.setDocumentId(document.getId());
        version.setVersionNo(1);
        version.setFileObjectId(fileObject.getId());
        version.setStatus("DRAFT");
        version.setCreatedBy(createdBy);
        archiveMapper.insertVersion(version);
        evictArchiveListCacheAfterCommit();
        return new ArchiveDtos.DocumentUploadData(employeeId, document.getId(), version.getId(),
                stored.objectKey(), stored.sha256(), stored.sizeBytes(), version.getStatus());
    }

    @Transactional(readOnly = true)
    public java.util.List<ArchiveDtos.DocumentData> listDocuments(long employeeId, DataScope scope) {
        Employee employee = employeeMapper.findByIdInScope(employeeId, scope.type().name(),
                scope.employeeId(), scope.departmentId());
        if (employee == null) {
            throw new DataScopeDeniedException();
        }
        return archiveMapper.listDocumentsByEmployeeInScope(employeeId, scope.type().name(),
                scope.employeeId(), scope.departmentId());
    }

    @Transactional(readOnly = true)
    public ArchiveDtos.ResourceAuthorizationData authorizeVersion(long versionId, DataScope scope) {
        ArchiveDtos.ResourceAuthorizationData resource = archiveMapper.findVersionAuthorizationInScope(
                versionId, scope.type().name(), scope.employeeId(), scope.departmentId());
        if (resource == null) {
            throw new DataScopeDeniedException();
        }
        return resource;
    }

    @Transactional(readOnly = true)
    public ArchiveDtos.ResourceAuthorizationData authorizeDocument(long documentId, DataScope scope) {
        ArchiveDtos.ResourceAuthorizationData resource = archiveMapper.findDocumentAuthorizationInScope(
                documentId, scope.type().name(), scope.employeeId(), scope.departmentId());
        if (resource == null) {
            throw new DataScopeDeniedException();
        }
        return resource;
    }

    @Transactional(readOnly = true)
    public java.util.List<ArchiveDtos.VersionData> listVersions(long documentId, DataScope scope) {
        java.util.List<ArchiveDtos.VersionData> versions = archiveMapper.listVersionsByDocumentInScope(
                documentId, scope.type().name(), scope.employeeId(), scope.departmentId());
        if (versions.isEmpty()) {
            throw new DataScopeDeniedException();
        }
        return versions;
    }

    @Transactional(readOnly = true)
    public ArchiveDtos.DownloadData downloadVersion(long versionId, DataScope scope) {
        ArchiveVersion version = requireVersion(versionId, scope);
        FileObject file = version.getFileObject();
        if (file == null || file.getObjectKey() == null || file.getObjectKey().isBlank()) {
            throw new ArchiveNotFoundException("Archive file", versionId);
        }
        ObjectStorage.StoredObject stored = objectStorage.get(file.getObjectKey());
        String contentType = stored.contentType() == null || stored.contentType().isBlank()
                ? file.getContentType() : stored.contentType();
        return new ArchiveDtos.DownloadData(safeFileName(file.getOriginalName()),
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                stored.content());
    }

    @Transactional
    public ArchiveDtos.OcrResultData runOcr(long versionId, String documentType, Long operatorId,
                                            DataScope scope) {
        ArchiveVersion version = requireVersion(versionId, scope);
        OcrBinding existing = bindingMapper.findByVersionIdInScope(versionId, scope.type().name(),
                scope.employeeId(), scope.departmentId());
        if (existing != null) {
            if (!"FAILED".equals(existing.getStatus())) {
                return toResult(existing, ocrClient.getTask(existing.getOcrTaskId()));
            }
            bindingMapper.deleteByVersionId(versionId);
        }
        FileObject file = version.getFileObject();
        String physicalKey = objectStorage.physicalKey(file.getObjectKey());
        if (physicalKey == null || physicalKey.isBlank()) {
            physicalKey = file.getObjectKey();
        }
        OcrSourceData source = new OcrSourceData(physicalKey, file.getOriginalName(),
                file.getContentType(), file.getSizeBytes(), file.getSha256());
        OcrTaskData task = ocrClient.createTask(source, requireDocumentType(documentType));
        OcrBinding binding = toBinding(versionId, task);
        bindingMapper.insert(binding);
        persistInitialConfirmations(binding, task, operatorId);
        return toResult(binding, task);
    }

    @Transactional(readOnly = true)
    public ArchiveDtos.OcrResultData getOcrResult(long versionId, DataScope scope) {
        requireVersion(versionId, scope);
        OcrBinding binding = bindingMapper.findByVersionIdInScope(versionId, scope.type().name(),
                scope.employeeId(), scope.departmentId());
        if (binding == null) {
            throw new DataScopeDeniedException();
        }
        return toResult(binding, ocrClient.getTask(binding.getOcrTaskId()));
    }

    @Transactional(readOnly = true)
    public byte[] detectionPreview(long bindingId, DataScope scope) {
        OcrBinding binding = bindingMapper.findByIdInScope(bindingId, scope.type().name(),
                scope.employeeId(), scope.departmentId());
        if (binding == null) {
            throw new DataScopeDeniedException();
        }
        return ocrClient.getDetectionPreview(binding.getOcrTaskId());
    }

    @Transactional
    public ArchiveDtos.OcrResultData correctField(long bindingId, String fieldCode, String value,
                                                  String reason, Long operatorId, DataScope scope) {
        OcrBinding binding = bindingMapper.findByIdInScope(bindingId, scope.type().name(),
                scope.employeeId(), scope.departmentId());
        if (binding == null) {
            throw new DataScopeDeniedException();
        }
        OcrTaskData task = ocrClient.correctField(binding.getOcrTaskId(), fieldCode, value,
                operatorId == null ? "unknown" : Long.toString(operatorId), reason);
        JsonNode field = findField(task.getFields(), fieldCode);
        OcrFieldConfirmation confirmation = new OcrFieldConfirmation();
        confirmation.setBindingId(bindingId);
        confirmation.setFieldCode(fieldCode);
        confirmation.setOriginalValue(field == null ? null : text(field, "value"));
        confirmation.setConfirmedValue(value.trim());
        confirmation.setConfidence(field == null ? null : number(field, "confidence"));
        confirmation.setOperatorId(operatorId);
        confirmation.setReason(reason);
        bindingMapper.insertConfirmation(confirmation);
        return toResult(binding, task);
    }

    private ArchiveVersion requireVersion(long versionId, DataScope scope) {
        ArchiveVersion version = archiveMapper.findVersionByIdInScope(versionId, scope.type().name(),
                scope.employeeId(), scope.departmentId());
        if (version == null) {
            throw new DataScopeDeniedException();
        }
        return version;
    }

    private OcrBinding toBinding(long versionId, OcrTaskData task) {
        OcrBinding binding = new OcrBinding();
        binding.setVersionId(versionId);
        binding.setOcrTaskId(task.getTaskId());
        binding.setStatus(task.getStatus());
        binding.setEngineVersion(task.getEngineVersion());
        JsonNode preview = task.getDetectionPreview();
        if (preview != null && !preview.isNull()) {
            binding.setDetectionPreviewUrl(text(preview, "url"));
            binding.setDetectionPreviewContentType(text(preview, "contentType"));
            binding.setDetectionPreviewSize(longValue(preview, "size"));
            binding.setDetectionPreviewSha256(text(preview, "sha256"));
            binding.setDetectionPreviewWidth(intValue(preview, "width"));
            binding.setDetectionPreviewHeight(intValue(preview, "height"));
        }
        binding.setFieldCount(task.getFields() != null && task.getFields().isArray()
                ? task.getFields().size() : 0);
        return binding;
    }

    private void persistInitialConfirmations(OcrBinding binding, OcrTaskData task, Long operatorId) {
        JsonNode fields = task.getFields();
        if (fields == null || !fields.isArray()) {
            return;
        }
        for (JsonNode field : fields) {
            String fieldCode = text(field, "fieldCode");
            String value = text(field, "value");
            if (fieldCode == null || value == null) {
                continue;
            }
            OcrFieldConfirmation confirmation = new OcrFieldConfirmation();
            confirmation.setBindingId(binding.getId());
            confirmation.setFieldCode(fieldCode);
            confirmation.setOriginalValue(value);
            confirmation.setConfirmedValue(value);
            confirmation.setConfidence(number(field, "confidence"));
            confirmation.setOperatorId(operatorId);
            confirmation.setReason("OCR initial result");
            bindingMapper.insertConfirmation(confirmation);
        }
    }

    private ArchiveDtos.OcrResultData toResult(OcrBinding binding, OcrTaskData task) {
        return new ArchiveDtos.OcrResultData(binding.getId(), binding.getVersionId(), task.getTaskId(),
                task.getStatus(), task.getEngineVersion(), task.getSourceFile(), task.getDetectionPreview(),
                task.getTextBlocks(), task.getFields(), task.getErrorMessage());
    }

    private JsonNode findField(JsonNode fields, String fieldCode) {
        if (fields == null || !fields.isArray()) {
            return null;
        }
        for (JsonNode field : fields) {
            if (fieldCode.equals(text(field, "fieldCode"))) {
                return field;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Double number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? null : value.asDouble();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? null : value.asLong();
    }

    private Integer intValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? null : value.asInt();
    }

    private byte[] readAndVerifyImage(MultipartFile file, String contentType) {
        try {
            byte[] content = file.getBytes();
            if (ImageIO.read(new ByteArrayInputStream(content)) == null) {
                throw new InvalidImageException("Uploaded file is not a valid image");
            }
            return content;
        } catch (IOException exception) {
            throw new InvalidImageException("Uploaded image could not be read");
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
    }

    private boolean isImageType(String contentType) {
        return PNG.equals(contentType) || JPEG.equals(contentType) || JPG.equals(contentType);
    }

    private String extension(String contentType) {
        return PNG.equals(contentType) ? ".png" : ".jpg";
    }

    private String requireDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank() || documentType.length() > 64) {
            throw new IllegalArgumentException("documentType must not be blank");
        }
        return documentType.trim();
    }

    private void evictArchiveListCacheAfterCommit() {
        Runnable evict = () -> {
            try {
                Set<String> keys = redis.keys(CacheKeys.archiveListPrefix() + "*");
                if (keys != null && !keys.isEmpty()) {
                    redis.delete(keys);
                }
            } catch (RuntimeException exception) {
                log.debug("Archive list cache eviction failed", exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "archive-download";
        }
        String normalized = originalName.trim().replace('/', '_').replace('\\', '_');
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }
}
