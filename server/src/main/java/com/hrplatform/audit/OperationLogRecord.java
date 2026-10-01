package com.hrplatform.audit;

import java.time.LocalDateTime;

public record OperationLogRecord(Long id, Long operatorId, String action, String objectType,
                                 Long objectId, String result, String traceId, LocalDateTime createdAt) {
}
