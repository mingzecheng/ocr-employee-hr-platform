package com.hrplatform.workflow.request;

public class RequestNotFoundException extends RuntimeException {
    public RequestNotFoundException(long id) { super("HR request not found: " + id); }
}
