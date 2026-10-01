package com.hrplatform.archive.config;

import com.hrplatform.archive.ocr.HttpOcrClient;
import com.hrplatform.archive.ocr.OcrClient;
import com.hrplatform.archive.storage.MinioObjectStorage;
import com.hrplatform.archive.storage.ObjectStorage;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ArchiveExternalConfiguration {

    @Bean
    public MinioClient minioClient(@Value("${archive.minio.endpoint}") String endpoint,
                                   @Value("${archive.minio.access-key}") String accessKey,
                                   @Value("${archive.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public ObjectStorage objectStorage(MinioClient minioClient,
                                       @Value("${archive.minio.bucket}") String bucket,
                                       @Value("${archive.storage-prefix}") String prefix) {
        return new MinioObjectStorage(minioClient, bucket, prefix);
    }

    @Bean
    public OcrClient ocrClient(RestClient.Builder builder,
                               @Value("${archive.ocr-service-url}") String baseUrl,
                               @Value("${archive.ocr-connect-timeout:3s}") Duration connectTimeout,
                               @Value("${archive.ocr-read-timeout:120s}") Duration readTimeout,
                               @Value("${archive.ocr-internal-token:local-ocr-internal-token}") String internalToken) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new HttpOcrClient(builder
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build(), internalToken);
    }

    public OcrClient ocrClient(RestClient.Builder builder, String baseUrl) {
        return ocrClient(builder, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(120),
                "local-ocr-internal-token");
    }
}
