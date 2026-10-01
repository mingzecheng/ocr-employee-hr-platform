package com.hrplatform.archive;

public record FileObject(Long id, String objectKey, String originalName, String contentType, long size, String sha256) {
}
