package com.hrplatform.archive;

import io.minio.MinioClient;
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
        return new MinioObjectStorage(
                MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build(), bucket
        );
    }
}
