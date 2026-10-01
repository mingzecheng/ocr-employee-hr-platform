package com.hrplatform.archive.api;

public class ArchiveNotFoundException extends RuntimeException {
    public ArchiveNotFoundException(String resource, long id) {
        super(resource + " not found: " + id);
    }
}
