package com.hrplatform.audit;

import java.time.LocalDateTime;

/**
 * 审计日志完整详情（仅 SYSTEM_ADMIN 可见）：含完整风控指纹与变更前后快照。
 * 历史记录风控字段为 null，前端按 "-" 展示。
 *
 * <p>{@code hasChanges} 由后端根据 beforeSummary / afterSummary 是否非空推导：
 * 读操作（LOGIN / LOGOUT / REFRESH / QUERY / VIEW 等）两者皆为 null，前端据此隐藏"变更详情"区块。
 */
public record OperationLogDetail(Long id, Long operatorId, String operatorName, String action,
                                 String objectType, Long objectId, String result, LocalDateTime createdAt,
                                 String traceId, String clientIp, String userAgent, String requestMethod,
                                 String requestUri, String sessionId, String beforeSummary, String afterSummary,
                                 boolean hasChanges) {
}
