package com.hrplatform.archive;

public class InvalidArchiveFileException extends RuntimeException {
    public InvalidArchiveFileException(String message) {
        super(message);
    }
}
