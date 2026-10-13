package com.hrplatform.access;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hr.archive-access.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ArchiveAccessExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(ArchiveAccessExpiryScheduler.class);
    private final ArchiveAccessService service;

    public ArchiveAccessExpiryScheduler(ArchiveAccessService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${hr.archive-access.expiry-delay-ms:60000}")
    public void markOverdueAccessAsAbnormal() {
        try {
            service.expireOverdueInUseApplications();
        } catch (RuntimeException exception) {
            log.error("Failed to mark overdue archive access applications", exception);
        }
        try {
            service.expireUnusedApprovedApplications();
        } catch (RuntimeException exception) {
            log.error("Failed to expire unused archive access applications", exception);
        }
    }
}
