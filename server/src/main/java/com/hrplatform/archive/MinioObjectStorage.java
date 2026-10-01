package com.hrplatform.archive;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorage(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, String contentType, byte[] content) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw new StorageException("对象存储写入失败", exception);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try (var stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
             var output = new ByteArrayOutputStream()) {
            stream.transferTo(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            throw new StorageException("对象存储读取失败", exception);
        } catch (Exception exception) {
            throw new StorageException("对象存储读取失败", exception);
        }
    }
}
