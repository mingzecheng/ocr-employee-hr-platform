package com.hrplatform.inventory;

public record InventoryItem(Long id, Long taskId, Long expectedVersionId, Long actualVersionId,
                            Long employeeId, Long departmentId, String actualStatus,
                            String differenceType, String differenceDescription, Long checkedBy) {
    public InventoryItem(Long id, Long taskId, Long expectedVersionId, Long actualVersionId,
                         String actualStatus, String differenceType) {
        this(id, taskId, expectedVersionId, actualVersionId, null, null, actualStatus, differenceType, null, null);
    }
}
