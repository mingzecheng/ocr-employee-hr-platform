package com.hrplatform.archive.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final String bucket;
    private final String prefix;

    public MinioObjectStorage(MinioClient client, String bucket, String prefix) {
        this.client = client;
        this.bucket = bucket;
        this.prefix = prefix == null ? "" : prefix.replaceAll("/+$", "");
    }

    @Override
    public StoredObject put(String objectKey, String contentType, byte[] content) {
        String key = qualifiedKey(objectKey);
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
            return new StoredObject(objectKey, contentType, content.length, sha256(content), content);
        } catch (Exception exception) {
            throw new StorageException("Failed to store archive object", exception);
        }
    }

    @Override
    public StoredObject get(String objectKey) {
        String key = qualifiedKey(objectKey);
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(key).build());
            try (InputStream input = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(key).build())) {
                byte[] content = input.readAllBytes();
                return new StoredObject(objectKey, stat.contentType(), content.length, sha256(content), content);
            }
        } catch (Exception exception) {
            throw new StorageException("Failed to read archive object", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    @Override
    public String physicalKey(String objectKey) {
        return qualifiedKey(objectKey);
    }

    private String qualifiedKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/")
                || objectKey.contains("..")) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return prefix.isBlank() ? objectKey : prefix + "/" + objectKey;
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
