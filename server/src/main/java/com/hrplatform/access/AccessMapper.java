package com.hrplatform.access;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccessMapper {
    ArchiveAccessApplication insertApplication(ArchiveAccessApplication application);
    ArchiveAccessItem insertItem(ArchiveAccessItem item);
    ArchiveAccessApplication findApplication(@Param("id") Long id, @Param("employeeId") Long employeeId,
                                             @Param("departmentId") Long departmentId,
                                             @Param("scopeType") String scopeType);
    List<ArchiveAccessItem> listItems(@Param("applicationId") Long applicationId);
    int updateState(ArchiveAccessApplication application);
    ArchiveUseRecord insertUse(ArchiveUseRecord record);
    ArchiveUseRecord findActiveUse(@Param("applicationId") Long applicationId);
    int closeUse(ArchiveUseRecord record);

    List<ArchiveAccessApplication> listApplications(@Param("applicantId") Long applicantId,
                                                     @Param("status") String status,
                                                     @Param("departmentId") Long departmentId,
                                                     @Param("scopeType") String scopeType,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);

    long countApplications(@Param("applicantId") Long applicantId, @Param("status") String status,
                           @Param("departmentId") Long departmentId, @Param("scopeType") String scopeType);
}
