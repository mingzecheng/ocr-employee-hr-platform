package com.hrplatform.archive;

public interface ObjectStorage {
    void put(String objectKey, String contentType, byte[] content);

    byte[] get(String objectKey);
}
