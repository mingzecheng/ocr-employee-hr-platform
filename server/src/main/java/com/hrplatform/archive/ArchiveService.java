package com.hrplatform.archive;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.employee.DataScopeDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

@Service
public class ArchiveService {
    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);
    private final ArchiveMapper mapper;
    private final EmployeeMapper employeeMapper;
    private final ObjectStorage storage;
    private final OperationLogService operationLogService;
    private final ArchiveReadAuthorization readAuthorization;

    @Autowired
    public ArchiveService(ArchiveMapper mapper, EmployeeMapper employeeMapper, ObjectStorage storage,
                          OperationLogService operationLogService, ArchiveReadAuthorization readAuthorization) {
        this.mapper = mapper;
        this.employeeMapper = employeeMapper;
        this.storage = storage;
        this.operationLogService = operationLogService;
        this.readAuthorization = readAuthorization;
    }

    public ArchiveService(ArchiveMapper mapper, EmployeeMapper employeeMapper, ObjectStorage storage,
                          OperationLogService operationLogService) {
        this(mapper, employeeMapper, storage, operationLogService, (versionId, uploadedBy, scope) -> false);
    }

    @Transactional
    public ArchiveVersion createVersion(Long employeeId, String documentType, String originalName,
                                        String contentType, byte[] content, DataScope scope, Long operatorId) {
        if (!"image/png".equalsIgnoreCase(contentType) && !"image/jpeg".equalsIgnoreCase(contentType)
                && !"image/jpg".equalsIgnoreCase(contentType)) {
            throw new InvalidArchiveFileException("仅支持 PNG 或 JPEG 图片");
        }
        validateImage(content);
        if (employeeMapper.findByIdWithScope(employeeId, scope.employeeId(), scope.departmentId(), scope.type().name()) == null) {
            throw new InvalidArchiveFileException("员工不存在或无权访问");
        }
        ArchiveDocument document = mapper.findDocumentForUpdate(employeeId, documentType);
        if (document == null) {
            if (mapper.insertDocument(new ArchiveDocument(null, employeeId, documentType, documentType)) != 1) {
                throw new IllegalStateException("档案材料目录创建失败");
            }
            document = mapper.findDocumentForUpdate(employeeId, documentType);
            if (document == null) {
                throw new IllegalStateException("档案材料目录已写入但无法读取");
            }
        }
        int versionNo = mapper.nextVersionNumber(document.id());
        String safeName = originalName == null || originalName.isBlank() ? "upload" : originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        String objectKey = "archive/" + employeeId + "/" + UUID.randomUUID() + "/" + safeName;
        String sha256 = sha256(content);
        boolean objectUploaded = false;
        try {
            storage.put(objectKey, contentType.toLowerCase(Locale.ROOT), content);
            objectUploaded = true;
            FileObject draftFile = new FileObject(null, objectKey, safeName, contentType, content.length, sha256);
            if (mapper.insertFile(draftFile) != 1) {
                throw new IllegalStateException("档案文件元数据写入失败");
            }
            FileObject fileObject = mapper.findFileByObjectKey(objectKey);
            if (fileObject == null) {
                throw new IllegalStateException("档案文件元数据已写入但无法读取");
            }
            mapper.clearCurrent(document.id());
            ArchiveVersion draftVersion = new ArchiveVersion(
                    null, document.id(), versionNo, fileObject.id(), safeName, contentType, content.length,
                    sha256, "UPLOADED", true, LocalDateTime.now(), operatorId, "initial upload"
            );
            if (mapper.insertVersion(draftVersion) != 1) {
                throw new IllegalStateException("档案版本写入失败");
            }
            ArchiveVersion version = mapper.findVersionByDocumentAndNumber(document.id(), versionNo);
            if (version == null) {
                throw new IllegalStateException("档案版本已写入但无法读取");
            }
            operationLogService.record(operatorId, "UPLOAD", "ARCHIVE_VERSION", version.id(), "SUCCESS");
            return version;
        } catch (RuntimeException failure) {
            if (objectUploaded) {
                try {
                    storage.delete(objectKey);
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                    log.error("档案元数据写入失败且对象补偿删除失败: {}", objectKey, cleanupFailure);
                }
            }
            throw failure;
        }
    }

    public ArchiveDownload download(Long versionId, DataScope scope) {
        ArchiveVersion version = mapper.findVersionByIdWithScope(versionId, scope.employeeId(), scope.departmentId(), scope.type().name());
        if (version == null) {
            throw new InvalidArchiveFileException("档案版本不存在或无权访问");
        }
        if (!readAuthorization.allowsRead(versionId, version.createdBy(), scope)) {
            throw new ArchiveReadDeniedException(versionId);
        }
        String objectKey = mapper.findObjectKeyByVersionId(versionId);
        if (objectKey == null) {
            throw new InvalidArchiveFileException("档案文件不存在");
        }
        byte[] content = storage.get(objectKey);
        operationLogService.record(scope.userId(), "DOWNLOAD", "ARCHIVE_VERSION", versionId, "SUCCESS");
        return new ArchiveDownload(version.originalName(), version.contentType(), content);
    }

    @Transactional(readOnly = true)
    public ArchiveDocumentPage listDocuments(Long employeeId, int page, int pageSize, DataScope scope) {
        requireEmployeeScope(employeeId, scope);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<ArchiveDocument> items = mapper.listDocuments(employeeId, scope.departmentId(), scope.type().name(),
                (safePage - 1) * safeSize, safeSize);
        return new ArchiveDocumentPage(items, mapper.countDocuments(employeeId, scope.departmentId(), scope.type().name()),
                safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public ArchiveVersionPage listVersions(Long documentId, int page, int pageSize, DataScope scope) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        List<ArchiveVersion> items = mapper.listVersions(documentId, scope.employeeId(), scope.departmentId(),
                scope.type().name(), (safePage - 1) * safeSize, safeSize);
        return new ArchiveVersionPage(items, mapper.countVersions(documentId, scope.employeeId(), scope.departmentId(),
                scope.type().name()), safePage, safeSize);
    }

    private void requireEmployeeScope(Long employeeId, DataScope scope) {
        if (employeeId == null || employeeMapper.findByIdWithScope(employeeId, scope.employeeId(),
                scope.departmentId(), scope.type().name()) == null) {
            throw new DataScopeDeniedException(employeeId);
        }
    }

    private void validateImage(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidArchiveFileException("上传文件不能为空");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new InvalidArchiveFileException("文件不是有效图片");
            }
        } catch (IOException exception) {
            throw new InvalidArchiveFileException("文件不是有效图片");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
