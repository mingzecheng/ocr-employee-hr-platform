package com.hrplatform.workflow;

import java.time.LocalDateTime;

public record HrRequest(Long id, String requestNo, String requestType, Long employeeId,
                        Long targetDepartmentId, Long targetPositionId, Long applicantId,
                        Long sourceVersionId, String payloadJson, String status, String currentNode,
                        int versionNo, LocalDateTime createdAt, LocalDateTime submittedAt,
                        LocalDateTime completedAt) {
    public HrRequest withState(String nextStatus, String nextNode, int nextVersion,
                               LocalDateTime submitted, LocalDateTime completed) {
        return new HrRequest(id, requestNo, requestType, employeeId, targetDepartmentId,
                targetPositionId, applicantId, sourceVersionId, payloadJson, nextStatus,
                nextNode, nextVersion, createdAt, submitted, completed);
    }
}
