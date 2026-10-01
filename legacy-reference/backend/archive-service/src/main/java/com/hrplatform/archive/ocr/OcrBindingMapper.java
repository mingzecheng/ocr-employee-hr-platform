package com.hrplatform.archive.ocr;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OcrBindingMapper {
    int insert(OcrBinding binding);
    int deleteByVersionId(@Param("versionId") long versionId);
    OcrBinding findByIdInScope(@Param("id") long id,
                               @Param("scopeType") String scopeType,
                               @Param("employeeId") Long employeeId,
                               @Param("departmentId") Long departmentId);
    OcrBinding findByVersionIdInScope(@Param("versionId") long versionId,
                                      @Param("scopeType") String scopeType,
                                      @Param("employeeId") Long employeeId,
                                      @Param("departmentId") Long departmentId);
    List<OcrFailureData> listFailuresInScope(@Param("limit") int limit,
                                             @Param("scopeType") String scopeType,
                                             @Param("employeeId") Long employeeId,
                                             @Param("departmentId") Long departmentId);
    int insertConfirmation(OcrFieldConfirmation confirmation);
}
