package com.hrplatform.workflow.authorization;

public class WorkflowAuthorizationException extends RuntimeException {
    public WorkflowAuthorizationException(Throwable cause) {
        super("Archive authorization service is unavailable", cause);
    }
}
