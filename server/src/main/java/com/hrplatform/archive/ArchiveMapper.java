package com.hrplatform.archive;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArchiveMapper {
    java.util.List<ArchiveDocument> listDocuments(@Param("employeeId") Long employeeId,
                                                   @Param("departmentId") Long departmentId,
                                                   @Param("scopeType") String scopeType,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    long countDocuments(@Param("employeeId") Long employeeId,
                         @Param("departmentId") Long departmentId,
                         @Param("scopeType") String scopeType);

    java.util.List<ArchiveVersion> listVersions(@Param("documentId") Long documentId,
                                                 @Param("employeeId") Long employeeId,
                                                 @Param("departmentId") Long departmentId,
                                                 @Param("scopeType") String scopeType,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    long countVersions(@Param("documentId") Long documentId,
                       @Param("employeeId") Long employeeId,
                       @Param("departmentId") Long departmentId,
                       @Param("scopeType") String scopeType);

    ArchiveDocument findDocument(@Param("employeeId") Long employeeId, @Param("documentType") String documentType);

    int nextVersionNumber(Long documentId);

    ArchiveDocument insertDocument(ArchiveDocument document);

    FileObject insertFile(FileObject fileObject);

    ArchiveVersion insertVersion(ArchiveVersion version);

    void clearCurrent(Long documentId);

    ArchiveVersion findVersionByIdWithScope(@Param("versionId") Long versionId,
                                             @Param("employeeId") Long employeeId,
                                             @Param("departmentId") Long departmentId,
                                             @Param("scopeType") String scopeType);

    String findObjectKeyByVersionId(@Param("versionId") Long versionId);

    ArchiveOcrSource findOcrSourceByVersionIdWithScope(@Param("versionId") Long versionId,
                                                        @Param("employeeId") Long employeeId,
                                                        @Param("departmentId") Long departmentId,
                                                        @Param("scopeType") String scopeType);
}
