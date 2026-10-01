package com.hrplatform.archive;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.employee.EmployeeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class ArchiveService {
    private final ArchiveMapper mapper;
    private final EmployeeMapper employeeMapper;
    private final ObjectStorage storage;
    private final OperationLogService operationLogService;

    public ArchiveService(ArchiveMapper mapper, EmployeeMapper employeeMapper, ObjectStorage storage,
                          OperationLogService operationLogService) {
        this.mapper = mapper;
        this.employeeMapper = employeeMapper;
        this.storage = storage;
        this.operationLogService = operationLogService;
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
        ArchiveDocument document = mapper.findDocument(employeeId, documentType);
        if (document == null) {
            document = mapper.insertDocument(new ArchiveDocument(null, employeeId, documentType, documentType));
        }
        int versionNo = mapper.nextVersionNumber(document.id());
        String safeName = originalName == null || originalName.isBlank() ? "upload" : originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        String objectKey = "archive/" + employeeId + "/" + UUID.randomUUID() + "/" + safeName;
        String sha256 = sha256(content);
        storage.put(objectKey, contentType.toLowerCase(Locale.ROOT), content);
        FileObject fileObject = mapper.insertFile(new FileObject(null, objectKey, safeName, contentType, content.length, sha256));
        mapper.clearCurrent(document.id());
        ArchiveVersion version = mapper.insertVersion(new ArchiveVersion(
                null, document.id(), versionNo, fileObject.id(), safeName, contentType, content.length,
                sha256, "UPLOADED", true, LocalDateTime.now(), operatorId, "initial upload"
        ));
        operationLogService.record(operatorId, "UPLOAD", "ARCHIVE_VERSION", version.id(), "SUCCESS");
        return version;
    }

    public ArchiveDownload download(Long versionId, DataScope scope) {
        ArchiveVersion version = mapper.findVersionByIdWithScope(versionId, scope.employeeId(), scope.departmentId(), scope.type().name());
        if (version == null) {
            throw new InvalidArchiveFileException("档案版本不存在或无权访问");
        }
        String objectKey = mapper.findObjectKeyByVersionId(versionId);
        if (objectKey == null) {
            throw new InvalidArchiveFileException("档案文件不存在");
        }
        return new ArchiveDownload(version.originalName(), version.contentType(), storage.get(objectKey));
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
