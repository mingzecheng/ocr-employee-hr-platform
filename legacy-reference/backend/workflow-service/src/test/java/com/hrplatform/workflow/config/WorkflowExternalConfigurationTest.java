package com.hrplatform.workflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowExternalConfigurationTest {

    @Test
    void exposesRedisConnectionSettingsThroughEnvironmentVariables() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "workflow-application", new ClassPathResource("application.yml"));

        assertThat(property(sources, "spring.data.redis.host"))
                .isEqualTo("${REDIS_HOST:127.0.0.1}");
        assertThat(property(sources, "spring.data.redis.port"))
                .isEqualTo("${REDIS_PORT:6379}");
        assertThat(property(sources, "spring.data.redis.timeout"))
                .isEqualTo("${REDIS_TIMEOUT:2s}");
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
