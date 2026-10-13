package com.hrplatform.access;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface AccessMapper {
    int countActiveReadGrants(@Param("versionId") Long versionId, @Param("userId") Long userId,
                              @Param("canReview") boolean canReview,
                              @Param("now") LocalDateTime now);

    int insertApplication(ArchiveAccessApplication application);
    int insertItem(ArchiveAccessItem item);
    ArchiveAccessApplication findByApplicationNo(@Param("applicationNo") String applicationNo);
    ArchiveAccessApplication findApplication(@Param("id") Long id, @Param("employeeId") Long employeeId,
                                             @Param("departmentId") Long departmentId,
                                             @Param("scopeType") String scopeType);
    List<ArchiveAccessItem> listItems(@Param("applicationId") Long applicationId);
    ArchiveAccessDisplayContext findDisplayContext(@Param("applicationId") Long applicationId);
    List<ArchiveAccessDetailItem> listDetailItems(@Param("applicationId") Long applicationId);
    int insertResolution(ArchiveAccessResolution resolution);
    List<ArchiveAccessResolution> listResolutions(@Param("applicationId") Long applicationId);
    int insertDecision(ArchiveAccessDecision decision);
    List<ArchiveAccessDecision> listDecisions(@Param("applicationId") Long applicationId);
    int updateState(ArchiveAccessApplication application);
    int insertUse(ArchiveUseRecord record);
    ArchiveUseRecord findActiveUse(@Param("applicationId") Long applicationId);
    int closeUse(ArchiveUseRecord record);
    List<ArchiveUseRecord> listUses(@Param("applicationId") Long applicationId);
    List<ArchiveUseDetail> listUseDetails(@Param("applicationId") Long applicationId);
    List<Long> listOverdueInUseIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int markOverdueInUse(@Param("id") Long id, @Param("now") LocalDateTime now);
    List<Long> listExpiredApprovedIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
    int markApprovedExpired(@Param("id") Long id, @Param("now") LocalDateTime now);
    boolean isActiveEmployeeInDepartment(@Param("employeeId") Long employeeId,
                                        @Param("departmentId") Long departmentId);

    List<ArchiveAccessApplication> listApplications(@Param("applicantId") Long applicantId,
                                                     @Param("status") String status,
                                                     @Param("departmentId") Long departmentId,
                                                     @Param("scopeType") String scopeType,
                                                     @Param("scopeEmployeeId") Long scopeEmployeeId,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);

    long countApplications(@Param("applicantId") Long applicantId, @Param("status") String status,
                           @Param("departmentId") Long departmentId, @Param("scopeType") String scopeType,
                           @Param("scopeEmployeeId") Long scopeEmployeeId);
}
