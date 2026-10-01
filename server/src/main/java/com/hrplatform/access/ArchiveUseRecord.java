package com.hrplatform.access;

import java.time.LocalDateTime;

public record ArchiveUseRecord(Long id, Long applicationId, Long operatorId, LocalDateTime usedAt,
                               LocalDateTime returnedAt, String returnStatus, String remark) {
}
