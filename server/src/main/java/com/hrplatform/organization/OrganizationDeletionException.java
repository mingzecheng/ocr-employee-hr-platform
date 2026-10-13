package com.hrplatform.organization;

/**
 * Raised when a department or position cannot be deleted because it still
 * references dependent records (children, positions, employees). Mapped to
 * HTTP 400 by the global exception handler so the frontend can surface the
 * conflict message verbatim.
 */
public class OrganizationDeletionException extends RuntimeException {
    public OrganizationDeletionException(String message) {
        super(message);
    }
}
