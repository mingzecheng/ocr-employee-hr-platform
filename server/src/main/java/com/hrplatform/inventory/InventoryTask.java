package com.hrplatform.inventory;

public record InventoryTask(Long id, String taskNo, String status, Long initiatorId) {
    public InventoryTask(Long id, String taskNo, String status) {
        this(id, taskNo, status, null);
    }
}
