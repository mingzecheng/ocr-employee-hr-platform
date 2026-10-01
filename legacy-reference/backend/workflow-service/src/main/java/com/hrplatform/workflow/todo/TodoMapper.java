package com.hrplatform.workflow.todo;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TodoMapper {
    List<TodoRow> findPendingHrRequests(@Param("scopeType") String scopeType,
                                        @Param("employeeId") Long employeeId,
                                        @Param("departmentId") Long departmentId,
                                        @Param("limit") int limit);

    List<TodoRow> findPendingArchiveAccess(@Param("scopeType") String scopeType,
                                           @Param("employeeId") Long employeeId,
                                           @Param("departmentId") Long departmentId,
                                           @Param("limit") int limit);

    List<TodoRow> findDueUses(@Param("scopeType") String scopeType,
                              @Param("employeeId") Long employeeId,
                              @Param("departmentId") Long departmentId,
                              @Param("now") LocalDateTime now,
                              @Param("dueSoonUntil") LocalDateTime dueSoonUntil,
                              @Param("limit") int limit);

    record TodoRow(Long id, String sourceType, String title, Long employeeId, String status,
                   LocalDateTime dueAt, LocalDateTime createdAt) {}
}
