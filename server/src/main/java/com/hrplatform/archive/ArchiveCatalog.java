package com.hrplatform.archive;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ArchiveCatalog {
    List<ArchiveVersion> listVersions(@Param("documentId") Long documentId,
                                      @Param("employeeId") Long employeeId,
                                      @Param("departmentId") Long departmentId,
                                      @Param("scopeType") String scopeType,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    ArchiveVersion findVersionByIdWithScope(@Param("versionId") Long versionId,
                                             @Param("employeeId") Long employeeId,
                                             @Param("departmentId") Long departmentId,
                                             @Param("scopeType") String scopeType);

    ArchiveOcrSource findOcrSourceByVersionIdWithScope(@Param("versionId") Long versionId,
                                                        @Param("employeeId") Long employeeId,
                                                        @Param("departmentId") Long departmentId,
                                                        @Param("scopeType") String scopeType);
}
