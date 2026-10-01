package com.hrplatform.archive.storage;

public interface ObjectStorage {
    StoredObject put(String objectKey, String contentType, byte[] content);

    StoredObject get(String objectKey);

    default String physicalKey(String objectKey) {
        return objectKey;
    }

    record StoredObject(String objectKey, String contentType, long sizeBytes, String sha256, byte[] content) {
    }
}
