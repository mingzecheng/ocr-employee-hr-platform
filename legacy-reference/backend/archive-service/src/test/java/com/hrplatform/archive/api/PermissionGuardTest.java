package com.hrplatform.archive.api;

import com.hrplatform.archive.statistics.StatisticsController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionGuardTest {
    @Test
    void archiveAndStatisticsEndpointsDeclareRequiredAuthorities() throws Exception {
        assertAuthority(ArchiveController.class, "createEmployee", "PERM_ARCHIVE_WRITE");
        assertAuthority(ArchiveController.class, "listEmployees", "PERM_ARCHIVE_READ");
        assertAuthority(ArchiveController.class, "uploadDocument", "PERM_ARCHIVE_WRITE");
        assertAuthority(ArchiveController.class, "runOcr", "PERM_ARCHIVE_WRITE");
        assertAuthority(ArchiveController.class, "getOcrResult", "PERM_ARCHIVE_READ");
        assertAuthority(ArchiveController.class, "getDetectionPreview", "PERM_ARCHIVE_READ");
        assertAuthority(ArchiveController.class, "correctField", "PERM_ARCHIVE_WRITE");
        assertAuthority(StatisticsController.class, "overview", "PERM_STATISTICS_READ");
    }

    private void assertAuthority(Class<?> type, String methodName, String permission) {
        Method method = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(permission);
    }
}
