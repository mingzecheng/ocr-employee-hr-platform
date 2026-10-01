package com.hrplatform.ocr;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OcrBindingMapper {
    OcrBinding findByVersionId(@Param("versionId") Long versionId);

    OcrBinding findByVersionIdWithScope(@Param("versionId") Long versionId,
                                        @Param("employeeId") Long employeeId,
                                        @Param("departmentId") Long departmentId,
                                        @Param("scopeType") String scopeType);

    OcrBinding findByIdWithScope(@Param("id") Long id,
                                 @Param("employeeId") Long employeeId,
                                 @Param("departmentId") Long departmentId,
                                 @Param("scopeType") String scopeType);

    OcrBinding insert(OcrBinding binding);

    OcrFieldRevision insertRevision(OcrFieldRevision revision);

    java.util.List<OcrFieldRevision> listRevisions(@Param("bindingId") Long bindingId);
}
