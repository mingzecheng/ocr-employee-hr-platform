package com.hrplatform.audit;

import java.time.LocalDateTime;

public record OperationLogRecord(Long id, Long operatorId, String action, String objectType,
                                 Long objectId, String beforeSummary, String afterSummary, String result,
                                 String traceId, LocalDateTime createdAt,
                                 String clientIp, String userAgent, String requestMethod,
                                 String requestUri, String sessionId) {
}
