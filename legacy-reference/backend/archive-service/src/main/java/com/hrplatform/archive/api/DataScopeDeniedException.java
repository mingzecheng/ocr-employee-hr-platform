package com.hrplatform.archive.api;

import org.springframework.security.access.AccessDeniedException;

public class DataScopeDeniedException extends AccessDeniedException {
    public DataScopeDeniedException() {
        super("Resource is outside the current data scope");
    }
}
