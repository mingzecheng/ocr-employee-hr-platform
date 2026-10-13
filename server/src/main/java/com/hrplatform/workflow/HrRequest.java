package com.hrplatform.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.time.LocalDate;

public record HrRequest(Long id, String requestNo, String requestType, Long employeeId,
                        Long targetDepartmentId, Long approvalDepartmentId, Long targetPositionId, Long applicantId,
                        Long sourceVersionId, @JsonIgnore String payloadJson, String status, String currentNode,
                        int versionNo, LocalDateTime createdAt, LocalDateTime submittedAt,
                        LocalDateTime completedAt, String leaveType, LocalDate leaveStartDate,
                        LocalDate leaveEndDate, String leaveReason, LocalDate effectiveDate,
                        String contractType, LocalDate leaveDate, String handoverPerson,
                        String handoverStatus, String reason, LocalDateTime employeeChangeAppliedAt) {
    public HrRequest(Long id, String requestNo, String requestType, Long employeeId,
                     Long targetDepartmentId, Long approvalDepartmentId, Long targetPositionId, Long applicantId,
                     Long sourceVersionId, String payloadJson, String status, String currentNode,
                     int versionNo, LocalDateTime createdAt, LocalDateTime submittedAt,
                     LocalDateTime completedAt, String leaveType, LocalDate leaveStartDate,
                     LocalDate leaveEndDate, String leaveReason, LocalDate effectiveDate,
                     String contractType, LocalDate leaveDate, String handoverPerson,
                     String handoverStatus, String reason) {
        this(id, requestNo, requestType, employeeId, targetDepartmentId, approvalDepartmentId, targetPositionId,
                applicantId, sourceVersionId, payloadJson, status, currentNode, versionNo, createdAt, submittedAt,
                completedAt, leaveType, leaveStartDate, leaveEndDate, leaveReason, effectiveDate, contractType,
                leaveDate, handoverPerson, handoverStatus, reason, null);
    }

    public HrRequest(Long id, String requestNo, String requestType, Long employeeId,
                     Long targetDepartmentId, Long approvalDepartmentId, Long targetPositionId, Long applicantId,
                     Long sourceVersionId, String payloadJson, String status, String currentNode,
                     int versionNo, LocalDateTime createdAt, LocalDateTime submittedAt,
                     LocalDateTime completedAt, String leaveType, LocalDate leaveStartDate,
                     LocalDate leaveEndDate, String leaveReason) {
        this(id, requestNo, requestType, employeeId, targetDepartmentId, approvalDepartmentId, targetPositionId,
                applicantId, sourceVersionId, payloadJson, status, currentNode, versionNo, createdAt, submittedAt,
                completedAt, leaveType, leaveStartDate, leaveEndDate, leaveReason, null, null, null, null, null, null);
    }

    public HrRequest withState(String nextStatus, String nextNode, int nextVersion,
                               LocalDateTime submitted, LocalDateTime completed) {
        return new HrRequest(id, requestNo, requestType, employeeId, targetDepartmentId,
                approvalDepartmentId, targetPositionId, applicantId, sourceVersionId, payloadJson, nextStatus,
                nextNode, nextVersion, createdAt, submitted, completed, leaveType, leaveStartDate,
                leaveEndDate, leaveReason, effectiveDate, contractType, leaveDate, handoverPerson,
                handoverStatus, reason, employeeChangeAppliedAt);
    }

    public HrRequest withEmployeeChangeAppliedAt(LocalDateTime appliedAt) {
        return new HrRequest(id, requestNo, requestType, employeeId, targetDepartmentId,
                approvalDepartmentId, targetPositionId, applicantId, sourceVersionId, payloadJson, status,
                currentNode, versionNo, createdAt, submittedAt, completedAt, leaveType, leaveStartDate,
                leaveEndDate, leaveReason, effectiveDate, contractType, leaveDate, handoverPerson,
                handoverStatus, reason, appliedAt);
    }
}
