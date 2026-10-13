package com.hrplatform.inventory;

public record InventoryItem(Long id, Long taskId, Long expectedVersionId, Integer expectedVersionNo,
                            Long actualVersionId,
                            Long employeeId, Long departmentId, String actualStatus,
                            String differenceType, String differenceDescription, Long checkedBy,
                            String resolutionStatus, String resolution, String employeeNo,
                            String employeeName, String documentTitle) {
    public InventoryItem(Long id, Long taskId, Long expectedVersionId, Integer expectedVersionNo,
                         Long actualVersionId, Long employeeId, Long departmentId, String actualStatus,
                         String differenceType, String differenceDescription, Long checkedBy,
                         String resolutionStatus, String resolution) {
        this(id, taskId, expectedVersionId, expectedVersionNo, actualVersionId, employeeId, departmentId,
                actualStatus, differenceType, differenceDescription, checkedBy, resolutionStatus, resolution,
                null, null, null);
    }
}
