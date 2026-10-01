package com.hrplatform.workflow.access;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.workflow.authorization.WorkflowAuthorizationClient;
import com.hrplatform.workflow.authorization.WorkflowDataScopeDeniedException;
import com.hrplatform.workflow.security.WorkflowActor;
import com.hrplatform.workflow.request.WorkflowAuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ArchiveAccessService {
    private static final DateTimeFormatter APPLICATION_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final ArchiveAccessMapper mapper;
    private final ObjectMapper objectMapper;
    private final WorkflowAuthorizationClient authorizationClient;

    @Autowired
    public ArchiveAccessService(ArchiveAccessMapper mapper, ObjectMapper objectMapper,
                                WorkflowAuthorizationClient authorizationClient) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.authorizationClient = authorizationClient;
    }

    @Deprecated
    public ArchiveAccessService(ArchiveAccessMapper mapper, ObjectMapper objectMapper) {
        this(mapper, objectMapper, new WorkflowAuthorizationClient() {
            @Override
            public AuthorizedResource authorizeEmployee(long employeeId, String bearerToken) {
                return new AuthorizedResource(employeeId, null);
            }

            @Override
            public AuthorizedResource authorizeDocument(long documentId, String bearerToken) {
                return new AuthorizedResource(0L, null);
            }

            @Override
            public AuthorizedResource authorizeVersion(long versionId, String bearerToken) {
                return new AuthorizedResource(0L, null);
            }
        });
    }

    @Transactional
    public ArchiveAccessDtos.AccessData create(ArchiveAccessDtos.CreateRequest request, long applicantId) {
        return createInternal(request, legacyActor(applicantId), false);
    }

    @Transactional
    public ArchiveAccessDtos.AccessData create(ArchiveAccessDtos.CreateRequest request, WorkflowActor actor) {
        return createInternal(request, actor, true);
    }

    private ArchiveAccessDtos.AccessData createInternal(ArchiveAccessDtos.CreateRequest request,
                                                        WorkflowActor actor,
                                                        boolean authorizeItems) {
        if (request.startAt() == null || request.dueAt() == null || !request.dueAt().isAfter(request.startAt())) {
            throw new IllegalArgumentException("dueAt must be after startAt");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("At least one archive item is required");
        }
        WorkflowAuthorizationClient.AuthorizedResource employee = authorizeItems
                ? authorizationClient.authorizeEmployee(request.employeeId(), actor.bearerToken())
                : new WorkflowAuthorizationClient.AuthorizedResource(request.employeeId(), null);
        if (employee == null || !Long.valueOf(request.employeeId()).equals(employee.employeeId())) {
            throw new WorkflowDataScopeDeniedException();
        }
        ArchiveAccessApplication application = new ArchiveAccessApplication();
        application.setApplicationNo("AA-" + APPLICATION_TIME.format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        application.setApplicantId(actor.userId());
        application.setEmployeeId(request.employeeId());
        application.setTargetDepartmentId(employee.departmentId());
        application.setUseType(normalizeUseType(request.useType()));
        application.setPurpose(request.purpose().trim());
        application.setStartAt(request.startAt());
        application.setDueAt(request.dueAt());
        application.setReturnRequired(request.returnRequired() == null || request.returnRequired());
        application.setStatus("DRAFT");
        application.setCurrentNode("DRAFT");
        mapper.insertApplication(application);

        for (ArchiveAccessDtos.ItemRequest itemRequest : request.items()) {
            if (itemRequest.archiveVersionId() != null && itemRequest.archiveVersionId() <= 0) {
                throw new IllegalArgumentException("archiveVersionId must be positive");
            }
            if (authorizeItems) {
                WorkflowAuthorizationClient.AuthorizedResource itemResource = itemRequest.archiveVersionId() == null
                        ? authorizationClient.authorizeDocument(itemRequest.archiveDocumentId(), actor.bearerToken())
                        : authorizationClient.authorizeVersion(itemRequest.archiveVersionId(), actor.bearerToken());
                if (itemResource == null || !Long.valueOf(request.employeeId()).equals(itemResource.employeeId())
                        || (itemRequest.archiveVersionId() != null
                        && !Long.valueOf(itemRequest.archiveDocumentId()).equals(itemResource.documentId()))) {
                    throw new WorkflowDataScopeDeniedException();
                }
            }
            ArchiveAccessItem item = new ArchiveAccessItem();
            item.setApplicationId(application.getId());
            item.setArchiveDocumentId(itemRequest.archiveDocumentId());
            item.setArchiveVersionId(itemRequest.archiveVersionId());
            item.setScope(itemRequest.scope().trim().toUpperCase(Locale.ROOT));
            mapper.insertItem(item);
        }
        return toData(application);
    }

    @Transactional
    public ArchiveAccessDtos.AccessData submit(long id, long operatorId) {
        return submit(id, legacyActor(operatorId));
    }

    @Transactional
    public ArchiveAccessDtos.AccessData submit(long id, WorkflowActor actor) {
        ArchiveAccessApplication application = requireApplication(id, actor);
        requireState(application, "DRAFT");
        if (!Long.valueOf(actor.userId()).equals(application.getApplicantId())) {
            throw new ArchiveAccessStateException("applicant " + application.getApplicantId(),
                    Long.toString(actor.userId()));
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.updateApplicationState(id, "PENDING_DEPT_APPROVAL", "DEPT_APPROVAL", now, null);
        audit(id, actor.userId(), "ACCESS_SUBMITTED", "submitted");
        application.setStatus("PENDING_DEPT_APPROVAL");
        application.setCurrentNode("DEPT_APPROVAL");
        application.setSubmittedAt(now);
        return toData(application);
    }

    @Transactional
    public ArchiveAccessDtos.AccessData approve(long id, long operatorId, boolean approved, String comment) {
        return approve(id, legacyActor(operatorId), approved, comment);
    }

    @Transactional
    public ArchiveAccessDtos.AccessData approve(long id, WorkflowActor actor, boolean approved, String comment) {
        ArchiveAccessApplication application = requireApplication(id, actor);
        String nextStatus;
        String nextNode;
        if ("PENDING_DEPT_APPROVAL".equals(application.getStatus())) {
            nextStatus = approved ? "PENDING_HR_APPROVAL" : "REJECTED";
            nextNode = approved ? "HR_APPROVAL" : "COMPLETED";
        } else if ("PENDING_HR_APPROVAL".equals(application.getStatus())) {
            nextStatus = approved ? "APPROVED" : "REJECTED";
            nextNode = "COMPLETED";
        } else {
            throw new ArchiveAccessStateException("PENDING_DEPT_APPROVAL or PENDING_HR_APPROVAL",
                    application.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        ArchiveAccessApproval approval = new ArchiveAccessApproval();
        approval.setApplicationId(id);
        approval.setNodeCode(application.getCurrentNode());
        approval.setApproverId(actor.userId());
        approval.setDecision(approved ? "APPROVED" : "REJECTED");
        approval.setComment(normalizeComment(comment));
        approval.setDecidedAt(now);
        mapper.insertApproval(approval);
        LocalDateTime completedAt = "REJECTED".equals(nextStatus) ? now : null;
        mapper.updateApplicationState(id, nextStatus, nextNode, application.getSubmittedAt(), completedAt);
        audit(id, actor.userId(), approved ? "ACCESS_APPROVED" : "ACCESS_REJECTED", approval.getComment());
        application.setStatus(nextStatus);
        application.setCurrentNode(nextNode);
        application.setCompletedAt(completedAt);
        return toData(application);
    }

    @Transactional
    public ArchiveAccessDtos.UseData checkout(long applicationId, long receiverId) {
        return checkout(applicationId, legacyActor(receiverId));
    }

    @Transactional
    public ArchiveAccessDtos.UseData checkout(long applicationId, WorkflowActor actor) {
        ArchiveAccessApplication application = requireApplication(applicationId, actor);
        requireState(application, "APPROVED");
        ArchiveUseRecord existingUse = legacy(actor)
                ? mapper.findUseByApplicationId(applicationId)
                : mapper.findUseByApplicationId(applicationId, actor.scope().type().name(),
                actor.scope().employeeId(), actor.scope().departmentId());
        if (existingUse != null) {
            throw new ArchiveAccessStateException("no existing use record", "existing use record");
        }
        LocalDateTime now = LocalDateTime.now();
        ArchiveUseRecord use = new ArchiveUseRecord();
        use.setApplicationId(applicationId);
        use.setReceiverId(actor.userId());
        use.setCheckedOutAt(now);
        use.setDueAt(application.getDueAt());
        use.setStatus("IN_USE");
        mapper.insertUse(use);
        mapper.updateApplicationState(applicationId, "IN_USE", "USE", application.getSubmittedAt(), null);
        audit(applicationId, actor.userId(), "ACCESS_CHECKED_OUT", "checked out");
        return toUseData(use);
    }

    @Transactional
    public ArchiveAccessDtos.UseData returnUse(long useId, long operatorId,
                                                ArchiveAccessDtos.ReturnRequest request) {
        return returnUse(useId, legacyActor(operatorId), request);
    }

    @Transactional
    public ArchiveAccessDtos.UseData returnUse(long useId, WorkflowActor actor,
                                                ArchiveAccessDtos.ReturnRequest request) {
        ArchiveUseRecord use = legacy(actor)
                ? mapper.findUseById(useId)
                : mapper.findUseById(useId, actor.scope().type().name(), actor.scope().employeeId(),
                actor.scope().departmentId());
        if (use == null) {
            throw new ArchiveAccessNotFoundException("Archive use record", useId);
        }
        requireUseState(use, "IN_USE");
        if (!Long.valueOf(actor.userId()).equals(use.getReceiverId())) {
            throw new ArchiveAccessStateException("receiver " + use.getReceiverId(), Long.toString(actor.userId()));
        }
        ArchiveAccessApplication application = requireApplication(use.getApplicationId(), actor);
        String returnType = normalizeReturnType(request.returnType());
        String missing = normalizeOptional(request.missingDescription());
        String damage = normalizeOptional(request.damageDescription());
        if ("HANDOVER".equals(returnType) && request.handoverToId() == null) {
            throw new IllegalArgumentException("handoverToId is required for HANDOVER");
        }
        if (request.handoverToId() != null && request.handoverToId() <= 0) {
            throw new IllegalArgumentException("handoverToId must be positive");
        }
        boolean overdue = use.getDueAt() != null && LocalDateTime.now().isAfter(use.getDueAt());
        boolean abnormal = overdue || missing != null || damage != null;
        String finalStatus = abnormal ? "ABNORMAL" : "RETURNED";
        String finalNode = abnormal ? "ABNORMAL" : "COMPLETED";
        LocalDateTime now = LocalDateTime.now();
        mapper.updateUseReturn(useId, finalStatus, now, request.handoverToId(), returnType, missing, damage);
        mapper.updateApplicationState(application.getId(), finalStatus, finalNode,
                application.getSubmittedAt(), now);
        String detail = abnormal ? "abnormal return" : "returned";
        audit(application.getId(), actor.userId(), "ACCESS_RETURNED", detail);
        use.setStatus(finalStatus);
        use.setReturnedAt(now);
        use.setReturnType(returnType);
        use.setHandoverToId(request.handoverToId());
        use.setMissingDescription(missing);
        use.setDamageDescription(damage);
        return toUseData(use);
    }

    @Transactional(readOnly = true)
    public ArchiveAccessDtos.AccessData get(long id) {
        return toData(requireApplication(id, legacyActor(0L)));
    }

    @Transactional(readOnly = true)
    public ArchiveAccessDtos.AccessData get(long id, WorkflowActor actor) {
        return toData(requireApplication(id, actor));
    }

    private ArchiveAccessApplication requireApplication(long id, WorkflowActor actor) {
        ArchiveAccessApplication application = legacy(actor)
                ? mapper.findApplicationById(id)
                : mapper.findApplicationById(id, actor.scope().type().name(), actor.scope().employeeId(),
                actor.scope().departmentId());
        if (application == null) {
            throw new ArchiveAccessNotFoundException("Archive access application", id);
        }
        return application;
    }

    private void requireState(ArchiveAccessApplication application, String expected) {
        if (!expected.equals(application.getStatus())) {
            throw new ArchiveAccessStateException(expected, application.getStatus());
        }
    }

    private void requireUseState(ArchiveUseRecord use, String expected) {
        if (!expected.equals(use.getStatus())) {
            throw new ArchiveAccessStateException(expected, use.getStatus());
        }
    }

    private ArchiveAccessDtos.AccessData toData(ArchiveAccessApplication application) {
        List<ArchiveAccessItem> items = mapper.findItems(application.getId());
        List<ArchiveAccessApproval> approvals = mapper.findApprovals(application.getId());
        ArchiveUseRecord use = mapper.findUseByApplicationId(application.getId());
        return new ArchiveAccessDtos.AccessData(application.getId(), application.getApplicationNo(),
                application.getApplicantId(), application.getEmployeeId(), application.getUseType(),
                application.getPurpose(), application.getStartAt(), application.getDueAt(),
                application.getReturnRequired(), application.getStatus(), application.getCurrentNode(),
                (items == null ? List.<ArchiveAccessItem>of() : items).stream().map(item ->
                        new ArchiveAccessDtos.ItemData(item.getId(), item.getArchiveDocumentId(),
                                item.getArchiveVersionId(), item.getScope())).toList(),
                (approvals == null ? List.<ArchiveAccessApproval>of() : approvals).stream().map(approval ->
                        new ArchiveAccessDtos.ApprovalData(approval.getId(), approval.getNodeCode(),
                                approval.getApproverId(), approval.getDecision(), approval.getComment(),
                                approval.getDecidedAt())).toList(),
                use == null ? null : toUseData(use));
    }

    private ArchiveAccessDtos.UseData toUseData(ArchiveUseRecord use) {
        return new ArchiveAccessDtos.UseData(use.getId(), use.getApplicationId(), use.getReceiverId(),
                use.getCheckedOutAt(), use.getDueAt(), use.getReturnedAt(), use.getReturnType(),
                use.getHandoverToId(), use.getMissingDescription(), use.getDamageDescription(), use.getStatus());
    }

    private void audit(long applicationId, long operatorId, String action, String detail) {
        WorkflowAuditLog audit = new WorkflowAuditLog();
        audit.setActorUserId(operatorId);
        audit.setActionCode(action);
        audit.setResourceType("ARCHIVE_ACCESS");
        audit.setResourceId(Long.toString(applicationId));
        try {
            audit.setDetailJson(objectMapper.writeValueAsString(detail == null ? "" : detail));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit detail could not be serialized", exception);
        }
        mapper.insertAudit(audit);
    }

    private String normalizeUseType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("DIGITAL_ACCESS") && !normalized.equals("PAPER_BORROW")) {
            throw new IllegalArgumentException("Unsupported archive use type");
        }
        return normalized;
    }

    private String normalizeReturnType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("NORMAL") && !normalized.equals("HANDOVER")) {
            throw new IllegalArgumentException("Unsupported return type");
        }
        return normalized;
    }

    private String normalizeComment(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private WorkflowActor legacyActor(long operatorId) {
        return new WorkflowActor(operatorId, DataScope.all(operatorId), "legacy-test-token");
    }

    private boolean legacy(WorkflowActor actor) {
        return "legacy-test-token".equals(actor.bearerToken());
    }
}
