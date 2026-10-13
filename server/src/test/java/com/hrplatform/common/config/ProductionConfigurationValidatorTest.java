package com.hrplatform.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationValidatorTest {
    @Test
    void acceptsExplicitProductionSecretsWhenDemoAccountsAreDisabled() {
        assertThatCode(() -> ProductionConfigurationValidator.validate(
                "random-production-jwt-secret-with-more-than-32-characters",
                "database-password-for-production-2026",
                "random-production-ocr-internal-token-with-32-characters",
                "production-minio-access",
                "production-minio-secret-2026",
                false
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsExampleSecretsAndEnabledDemoAccounts() {
        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(
                "local-development-secret-key-change-before-sharing-please",
                "change-me-application-password",
                "local-ocr-internal-token",
                "minioadmin",
                "change-me-minio-password",
                true
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("MYSQL_PASSWORD")
                .hasMessageContaining("OCR_INTERNAL_TOKEN")
                .hasMessageContaining("MINIO_ACCESS_KEY")
                .hasMessageContaining("MINIO_SECRET_KEY")
                .hasMessageContaining("PLATFORM_DEMO_ACCOUNTS_ENABLED");
    }

    @Test
    void productionProfileBindsFlywayPlaceholderAndRequiresExternalSecrets() throws IOException {
        var productionSource = new YamlPropertySourceLoader()
                .load("production", new ClassPathResource("application-production.yml")).get(0);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(productionSource);
        FlywayProperties flyway = Binder.get(environment)
                .bind("spring.flyway", Bindable.of(FlywayProperties.class)).get();

        assertThat(flyway.getPlaceholders()).containsEntry("demoAccountsEnabled", "false");
        assertThat(productionSource.getProperty("spring.datasource.master.password")).isEqualTo("${MYSQL_PASSWORD}");
        assertThat(productionSource.getProperty("platform.jwt.secret")).isEqualTo("${JWT_SECRET}");
    }
}
