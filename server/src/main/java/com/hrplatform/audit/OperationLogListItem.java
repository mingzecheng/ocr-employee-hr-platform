package com.hrplatform.audit;

import java.time.LocalDateTime;

/**
 * 审计日志列表行：仅暴露简要信息，IP 已脱敏，不下发完整风控指纹与变更前后快照。
 */
public record OperationLogListItem(Long id, Long operatorId, String action, String objectType,
                                   Long objectId, String result, LocalDateTime createdAt,
                                   String clientIpMasked) {
}
