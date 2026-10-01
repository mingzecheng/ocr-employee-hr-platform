package com.hrplatform.workflow.request;

public class RequestStateException extends RuntimeException {
    public RequestStateException(String expected, String actual) {
        super("Request must be " + expected + " but was " + actual);
    }
}
