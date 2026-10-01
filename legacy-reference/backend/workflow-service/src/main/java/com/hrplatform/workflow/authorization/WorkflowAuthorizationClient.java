package com.hrplatform.workflow.authorization;

public interface WorkflowAuthorizationClient {
    AuthorizedResource authorizeEmployee(long employeeId, String bearerToken);

    AuthorizedResource authorizeDocument(long documentId, String bearerToken);

    AuthorizedResource authorizeVersion(long versionId, String bearerToken);

    record AuthorizedResource(long employeeId, Long departmentId, Long documentId) {
        public AuthorizedResource(long employeeId, Long departmentId) {
            this(employeeId, departmentId, null);
        }
    }
}
