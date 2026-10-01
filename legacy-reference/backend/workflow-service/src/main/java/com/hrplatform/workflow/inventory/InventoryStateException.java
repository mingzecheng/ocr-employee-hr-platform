package com.hrplatform.workflow.inventory;

public class InventoryStateException extends RuntimeException {
    public InventoryStateException(String expected, String actual) {
        super("Expected " + expected + " but was " + actual);
    }
}
