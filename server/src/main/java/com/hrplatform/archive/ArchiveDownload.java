package com.hrplatform.archive;

public record ArchiveDownload(String originalName, String contentType, byte[] content) {
}
