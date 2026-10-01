package com.hrplatform.archive.document;

import com.hrplatform.archive.api.ArchiveDtos;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArchiveMapper {
    int insertArchiveRecord(@Param("employeeId") long employeeId);
    Long findArchiveRecordIdByEmployeeId(@Param("employeeId") long employeeId);
    int insertFileObject(FileObject fileObject);
    int insertDocument(ArchiveDocument document);
    int insertVersion(ArchiveVersion version);
    ArchiveVersion findVersionByIdInScope(@Param("id") long id,
                                          @Param("scopeType") String scopeType,
                                          @Param("employeeId") Long employeeId,
                                          @Param("departmentId") Long departmentId);
    ArchiveDtos.ResourceAuthorizationData findVersionAuthorizationInScope(
            @Param("id") long id,
            @Param("scopeType") String scopeType,
            @Param("employeeId") Long employeeId,
            @Param("departmentId") Long departmentId);
    ArchiveDtos.ResourceAuthorizationData findDocumentAuthorizationInScope(
            @Param("id") long id,
            @Param("scopeType") String scopeType,
            @Param("employeeId") Long employeeId,
            @Param("departmentId") Long departmentId);
    List<ArchiveDtos.DocumentData> listDocumentsByEmployeeInScope(
            @Param("employeeId") long employeeId,
            @Param("scopeType") String scopeType,
            @Param("scopeEmployeeId") Long scopeEmployeeId,
            @Param("departmentId") Long departmentId);
    List<ArchiveDtos.VersionData> listVersionsByDocumentInScope(
            @Param("documentId") long documentId,
            @Param("scopeType") String scopeType,
            @Param("scopeEmployeeId") Long scopeEmployeeId,
            @Param("departmentId") Long departmentId);
}
