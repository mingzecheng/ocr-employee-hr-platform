package com.hrplatform.workflow.access;

import com.hrplatform.workflow.request.WorkflowAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ArchiveAccessMapper {
    int insertApplication(ArchiveAccessApplication application);
    int insertItem(ArchiveAccessItem item);
    default ArchiveAccessApplication findApplicationById(long id) {
        return findApplicationById(id, "ALL", null, null);
    }
    ArchiveAccessApplication findApplicationById(@Param("id") long id,
                                                @Param("scopeType") String scopeType,
                                                @Param("employeeId") Long employeeId,
                                                @Param("departmentId") Long departmentId);
    List<ArchiveAccessItem> findItems(@Param("applicationId") long applicationId);
    List<ArchiveAccessApproval> findApprovals(@Param("applicationId") long applicationId);
    int updateApplicationState(@Param("id") long id, @Param("status") String status,
                               @Param("currentNode") String currentNode,
                               @Param("submittedAt") LocalDateTime submittedAt,
                               @Param("completedAt") LocalDateTime completedAt);
    int insertApproval(ArchiveAccessApproval approval);
    default ArchiveUseRecord findUseByApplicationId(long applicationId) {
        return findUseByApplicationId(applicationId, "ALL", null, null);
    }
    ArchiveUseRecord findUseByApplicationId(@Param("applicationId") long applicationId,
                                            @Param("scopeType") String scopeType,
                                            @Param("employeeId") Long employeeId,
                                            @Param("departmentId") Long departmentId);
    default ArchiveUseRecord findUseById(long id) {
        return findUseById(id, "ALL", null, null);
    }
    ArchiveUseRecord findUseById(@Param("id") long id,
                                 @Param("scopeType") String scopeType,
                                 @Param("employeeId") Long employeeId,
                                 @Param("departmentId") Long departmentId);
    int insertUse(ArchiveUseRecord useRecord);
    int updateUseReturn(@Param("id") long id, @Param("status") String status,
                        @Param("returnedAt") LocalDateTime returnedAt,
                        @Param("handoverToId") Long handoverToId,
                        @Param("returnType") String returnType,
                        @Param("missingDescription") String missingDescription,
                        @Param("damageDescription") String damageDescription);
    int insertAudit(WorkflowAuditLog auditLog);
}
