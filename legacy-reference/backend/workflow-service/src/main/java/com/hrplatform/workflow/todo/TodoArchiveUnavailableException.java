package com.hrplatform.workflow.todo;

public class TodoArchiveUnavailableException extends RuntimeException {
    public TodoArchiveUnavailableException(String message) { super(message); }
    public TodoArchiveUnavailableException(String message, Throwable cause) { super(message, cause); }
}
