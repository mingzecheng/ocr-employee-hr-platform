package com.hrplatform.workflow.request;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface RequestMapper {
    int insert(HrRequest request);
    default HrRequest findById(long id) {
        return findById(id, "ALL", null, null);
    }

    HrRequest findById(@Param("id") long id,
                       @Param("scopeType") String scopeType,
                       @Param("employeeId") Long employeeId,
                       @Param("departmentId") Long departmentId);
    int updateState(@Param("id") long id, @Param("status") String status,
                    @Param("currentNode") String currentNode,
                    @Param("submittedAt") LocalDateTime submittedAt,
                    @Param("completedAt") LocalDateTime completedAt);
    int insertApproval(ApprovalRecord approval);
    int insertAudit(WorkflowAuditLog auditLog);
}
