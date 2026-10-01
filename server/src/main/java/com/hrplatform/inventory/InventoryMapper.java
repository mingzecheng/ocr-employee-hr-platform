package com.hrplatform.inventory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryMapper {
    InventoryTask insertTask(InventoryTask task);
    InventoryItem insertItem(InventoryItem item);
    InventoryTask findTask(@Param("id") Long id, @Param("departmentId") Long departmentId,
                           @Param("scopeType") String scopeType);
    List<InventoryItem> listItems(@Param("taskId") Long taskId);
    int updateTask(InventoryTask task);
    int updateItem(InventoryItem item);
}
