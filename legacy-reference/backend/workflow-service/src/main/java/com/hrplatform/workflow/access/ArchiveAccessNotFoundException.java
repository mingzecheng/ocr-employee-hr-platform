package com.hrplatform.workflow.access;

public class ArchiveAccessNotFoundException extends RuntimeException {
    public ArchiveAccessNotFoundException(String resource, long id) {
        super(resource + " not found: " + id);
    }
}
