package com.hrplatform.workflow.inventory;

import com.hrplatform.workflow.request.WorkflowAuditLog;
import com.hrplatform.common.security.DataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryMapper {
    int insertTask(InventoryTask task);
    int insertItem(InventoryItem item);
    default InventoryTask findTaskById(long id) {
        return findTaskById(id, "ALL", null, null);
    }
    InventoryTask findTaskById(@Param("id") long id,
                               @Param("scopeType") String scopeType,
                               @Param("employeeId") Long employeeId,
                               @Param("departmentId") Long departmentId);
    default List<InventoryItem> findItems(long taskId) {
        return findItems(taskId, "ALL", null, null);
    }
    List<InventoryItem> findItems(@Param("taskId") long taskId,
                                  @Param("scopeType") String scopeType,
                                  @Param("employeeId") Long employeeId,
                                  @Param("departmentId") Long departmentId);
    default InventoryItem findItemById(long id) {
        return findItemById(id, "ALL", null, null);
    }
    InventoryItem findItemById(@Param("id") long id,
                               @Param("scopeType") String scopeType,
                               @Param("employeeId") Long employeeId,
                               @Param("departmentId") Long departmentId);
    int countItems(@Param("taskId") long taskId);
    int countItemsInScope(@Param("taskId") long taskId,
                          @Param("scopeType") String scopeType,
                          @Param("employeeId") Long employeeId,
                          @Param("departmentId") Long departmentId);
    int updateTaskState(@Param("id") long id, @Param("status") String status,
                        @Param("startedAt") LocalDateTime startedAt,
                        @Param("completedAt") LocalDateTime completedAt);
    int updateItemCheck(InventoryItem item);
    int insertResolution(InventoryResolution resolution);
    List<InventoryResolution> findResolutionByTaskId(@Param("taskId") long taskId);
    int insertAudit(WorkflowAuditLog auditLog);
}
