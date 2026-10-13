package com.hrplatform.archive;

public class ArchiveReadDeniedException extends RuntimeException {
    public ArchiveReadDeniedException(Long versionId) {
        super("档案版本尚无有效访问授权: " + versionId);
    }
}
