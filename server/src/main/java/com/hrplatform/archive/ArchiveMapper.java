package com.hrplatform.archive;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArchiveMapper {
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
