package com.hrplatform.inventory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryMapper {
    List<InventoryTask> listTasks(@Param("departmentId") Long departmentId,
                                  @Param("employeeId") Long employeeId,
                                  @Param("scopeType") String scopeType,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);
    long countTasks(@Param("departmentId") Long departmentId, @Param("employeeId") Long employeeId,
                    @Param("scopeType") String scopeType);
    int insertTask(InventoryTask task);
    int insertItem(InventoryItem item);
    InventoryTask findTaskByTaskNo(@Param("taskNo") String taskNo);
    InventoryTask findTask(@Param("id") Long id, @Param("departmentId") Long departmentId,
                           @Param("employeeId") Long employeeId,
                           @Param("scopeType") String scopeType);
    InventoryItem findItem(@Param("taskId") Long taskId, @Param("itemId") Long itemId,
                           @Param("departmentId") Long departmentId, @Param("employeeId") Long employeeId,
                           @Param("scopeType") String scopeType);
    List<InventoryItem> listItems(@Param("taskId") Long taskId,
                                  @Param("departmentId") Long departmentId,
                                  @Param("employeeId") Long employeeId,
                                  @Param("scopeType") String scopeType);
    List<InventoryItem> listAllItems(@Param("taskId") Long taskId);
    int recordCheck(InventoryItem item);
    int insertExceptionRecord(@Param("inventoryItemId") Long inventoryItemId,
                              @Param("exceptionType") String exceptionType,
                              @Param("description") String description);
    int resolveExceptionRecord(@Param("inventoryItemId") Long inventoryItemId,
                               @Param("operatorId") Long operatorId,
                               @Param("resolution") String resolution);
    int updateResolutionStatus(@Param("itemId") Long itemId, @Param("status") String status);
    int transitionTask(@Param("id") Long id, @Param("fromStatus") String fromStatus,
                       @Param("toStatus") String toStatus);
}
