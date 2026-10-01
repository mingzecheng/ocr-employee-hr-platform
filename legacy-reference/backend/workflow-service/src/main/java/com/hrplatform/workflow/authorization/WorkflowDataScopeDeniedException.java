package com.hrplatform.workflow.authorization;

public class WorkflowDataScopeDeniedException extends RuntimeException {
    public WorkflowDataScopeDeniedException() {
        super("Access denied");
    }
}
