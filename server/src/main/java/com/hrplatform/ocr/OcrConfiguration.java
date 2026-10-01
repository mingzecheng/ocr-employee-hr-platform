package com.hrplatform.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OcrConfiguration {
    @Bean
    OcrClient ocrClient(
            @Value("${platform.ocr.base-url}") String baseUrl,
            @Value("${platform.ocr.internal-token}") String internalToken
    ) {
        return HttpOcrClient.create(baseUrl, internalToken);
    }
}
