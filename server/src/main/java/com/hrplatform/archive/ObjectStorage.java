package com.hrplatform.archive;

public interface ObjectStorage {
    void put(String objectKey, String contentType, byte[] content);

    void delete(String objectKey);

    byte[] get(String objectKey);
}
