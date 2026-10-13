package com.hrplatform.common.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("production")
public class ProductionConfigurationValidator {
    public ProductionConfigurationValidator(Environment environment) {
        validate(
                environment.getProperty("platform.jwt.secret"),
                environment.getProperty("spring.datasource.master.password"),
                environment.getProperty("platform.ocr.internal-token"),
                environment.getProperty("platform.storage.access-key"),
                environment.getProperty("platform.storage.secret-key"),
                environment.getProperty("platform.demo-accounts-enabled", Boolean.class, true)
        );
    }

    static void validate(String jwtSecret, String databasePassword, String ocrToken,
                         String minioAccessKey, String minioSecretKey, boolean demoAccountsEnabled) {
        List<String> errors = new ArrayList<>();
        requireSecret(errors, "JWT_SECRET", jwtSecret, 32, "local-development-secret-key-change-before-sharing-please");
        requireSecret(errors, "MYSQL_PASSWORD", databasePassword, 16, "change-me-application-password");
        requireSecret(errors, "OCR_INTERNAL_TOKEN", ocrToken, 32, "local-ocr-internal-token");
        requireSecret(errors, "MINIO_ACCESS_KEY", minioAccessKey, 8, "minioadmin");
        requireSecret(errors, "MINIO_SECRET_KEY", minioSecretKey, 16, "change-me-minio-password");
        if (demoAccountsEnabled) {
            errors.add("PLATFORM_DEMO_ACCOUNTS_ENABLED must be false");
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Production startup refused: " + String.join(", ", errors));
        }
    }

    private static void requireSecret(List<String> errors, String name, String value, int minimumLength,
                                      String exampleValue) {
        if (value == null || value.isBlank() || value.length() < minimumLength
                || value.equals(exampleValue) || value.startsWith("local-")) {
            errors.add(name + " must be explicitly configured with a production value");
        }
    }
}
