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

    int insert(OcrBinding binding);

    int update(OcrBinding binding);

    int updateConfirmStatus(@Param("id") Long id,
                            @Param("confirmStatus") String confirmStatus,
                            @Param("confirmedBy") Long confirmedBy,
                            @Param("confirmedAt") java.time.LocalDateTime confirmedAt,
                            @Param("confirmNote") String confirmNote);

    int insertRevision(OcrFieldRevision revision);

    java.util.List<OcrFieldRevision> listRevisions(@Param("bindingId") Long bindingId);
}
