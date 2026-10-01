package com.hrplatform.identity.config;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdentitySeedInitializerTest {

    @Test
    void applicationRunnerOwnsTheSeedTransactionBoundary() throws NoSuchMethodException {
        Method run = IdentitySeedInitializer.class.getMethod(
                "run", org.springframework.boot.ApplicationArguments.class);

        assertThat(run.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void permissionMigrationDefinesBusinessPermissionsAndRoleAssignments() throws IOException {
        String migration = new String(new ClassPathResource(
                "db/migration/V3__identity_permissions.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        List<String> permissions = List.of(
                "ARCHIVE_READ", "ARCHIVE_WRITE", "ARCHIVE_ACCESS_CREATE", "ARCHIVE_ACCESS_APPROVE",
                "ARCHIVE_ACCESS_CHECKOUT", "ARCHIVE_ACCESS_RETURN", "INVENTORY_READ", "INVENTORY_WRITE",
                "STATISTICS_READ", "HR_REQUEST_READ", "HR_REQUEST_CREATE", "HR_REQUEST_APPROVE");

        assertThat(migration).contains(permissions.toArray(String[]::new));
        assertThat(migration).contains("WHERE r.code = 'SYSTEM_ADMIN'");
        assertThat(migration).contains("WHERE r.code = 'HR_ADMIN'");
        assertThat(migration).contains("WHERE r.code = 'DEPT_MANAGER'");
        assertThat(migration).contains("WHERE r.code = 'EMPLOYEE'");
    }
}
