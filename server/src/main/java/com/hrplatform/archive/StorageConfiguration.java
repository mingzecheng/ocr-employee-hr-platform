package com.hrplatform.archive;

import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfiguration {
    @Bean
    ObjectStorage objectStorage(
            @Value("${platform.storage.endpoint}") String endpoint,
            @Value("${platform.storage.access-key}") String accessKey,
            @Value("${platform.storage.secret-key}") String secretKey,
            @Value("${platform.storage.bucket}") String bucket
    ) {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("对象存储 bucket 初始化失败: " + bucket, exception);
        }
        return new MinioObjectStorage(client, bucket);
    }
}
