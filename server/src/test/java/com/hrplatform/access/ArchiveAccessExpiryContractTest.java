package com.hrplatform.access;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveAccessExpiryContractTest {
    @Test
    void serviceExposesAnOverdueUseSweepForTheScheduledJob() {
        boolean sweepExists = Arrays.stream(ArchiveAccessService.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("expireOverdueInUseApplications"));

        assertThat(sweepExists).isTrue();
    }

    @Test
    void serviceExposesASweepForApprovedApplicationsThatMissedTheirStartWindow() {
        boolean sweepExists = Arrays.stream(ArchiveAccessService.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("expireUnusedApprovedApplications"));

        assertThat(sweepExists).isTrue();
    }

    @Test
    void mapperExpiresOnlyApprovedApplicationsWhoseDeadlinePassed() throws Exception {
        String mapper = Files.readString(Path.of("src/main/resources/mapper/AccessMapper.xml"))
                .replaceAll("\\s+", " ");

        assertThat(mapper).contains("id=\"listExpiredApprovedIds\"");
        assertThat(mapper).contains("id=\"markApprovedExpired\"");
        assertThat(mapper).contains("WHERE id = #{id} AND status = 'APPROVED' AND due_at &lt; #{now}");
    }
}
