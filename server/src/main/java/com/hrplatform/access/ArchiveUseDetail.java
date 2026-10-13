package com.hrplatform.access;

import java.time.LocalDateTime;

public record ArchiveUseDetail(Long id, Long applicationId, Long operatorId, String operatorName,
                               LocalDateTime usedAt, LocalDateTime returnedAt, String returnStatus,
                               String remark, Long returnedByUserId, String returnedByName,
                               Long handoverToEmployeeId, String handoverToEmployeeNo,
                               String handoverToEmployeeName) {
}
