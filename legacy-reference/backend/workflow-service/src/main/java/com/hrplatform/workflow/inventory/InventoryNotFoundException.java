package com.hrplatform.workflow.inventory;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(String resource, long id) {
        super(resource + " " + id + " was not found");
    }
}
