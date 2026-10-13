package com.hrplatform.archive;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArchiveMapper extends ArchiveCatalog {
    java.util.List<ArchiveDocument> listDocuments(@Param("employeeId") Long employeeId,
                                                   @Param("departmentId") Long departmentId,
                                                   @Param("scopeType") String scopeType,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    long countDocuments(@Param("employeeId") Long employeeId,
                         @Param("departmentId") Long departmentId,
                         @Param("scopeType") String scopeType);

    long countVersions(@Param("documentId") Long documentId,
                       @Param("employeeId") Long employeeId,
                       @Param("departmentId") Long departmentId,
                       @Param("scopeType") String scopeType);

    ArchiveDocument findDocument(@Param("employeeId") Long employeeId, @Param("documentType") String documentType);

    int nextVersionNumber(Long documentId);

    ArchiveDocument findDocumentForUpdate(@Param("employeeId") Long employeeId,
                                          @Param("documentType") String documentType);

    int insertDocument(ArchiveDocument document);

    int insertFile(FileObject fileObject);

    FileObject findFileByObjectKey(@Param("objectKey") String objectKey);

    int insertVersion(ArchiveVersion version);

    ArchiveVersion findVersionByDocumentAndNumber(@Param("documentId") Long documentId,
                                                   @Param("versionNo") int versionNo);

    void clearCurrent(Long documentId);

    String findObjectKeyByVersionId(@Param("versionId") Long versionId);
}
