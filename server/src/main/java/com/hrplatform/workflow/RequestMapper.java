package com.hrplatform.workflow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RequestMapper {
    HrRequest insert(HrRequest request);

    HrRequest findByIdWithScope(@Param("id") Long id, @Param("employeeId") Long employeeId,
                                @Param("departmentId") Long departmentId,
                                @Param("scopeType") String scopeType);

    int updateState(HrRequest request);

    ApprovalRecord insertApproval(ApprovalRecord approval);

    java.util.List<HrRequest> list(@Param("requestType") String requestType,
                                   @Param("status") String status,
                                   @Param("employeeId") Long employeeId,
                                   @Param("departmentId") Long departmentId,
                                   @Param("scopeType") String scopeType,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long count(@Param("requestType") String requestType,
               @Param("status") String status,
               @Param("employeeId") Long employeeId,
               @Param("departmentId") Long departmentId,
               @Param("scopeType") String scopeType);
}
