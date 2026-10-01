package com.hrplatform.workflow.access;

public class ArchiveAccessStateException extends RuntimeException {
    public ArchiveAccessStateException(String expected, String actual) {
        super("Archive access application must be " + expected + " but was " + actual);
    }
}
