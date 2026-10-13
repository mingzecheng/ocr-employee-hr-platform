package com.hrplatform.workflow;

import java.time.LocalDateTime;

public record ApprovalRecord(Long id, Long requestId, String nodeCode, Long approverId,
                             String decision, String comment, LocalDateTime decidedAt,
                             String approverName) {
    public ApprovalRecord(Long id, Long requestId, String nodeCode, Long approverId,
                          String decision, String comment, LocalDateTime decidedAt) {
        this(id, requestId, nodeCode, approverId, decision, comment, decidedAt, null);
    }
}
