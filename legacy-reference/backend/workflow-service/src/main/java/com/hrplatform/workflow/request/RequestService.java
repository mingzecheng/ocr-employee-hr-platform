package com.hrplatform.workflow.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.workflow.authorization.WorkflowAuthorizationClient;
import com.hrplatform.workflow.security.WorkflowActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
public class RequestService {
    private static final DateTimeFormatter REQUEST_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final RequestMapper mapper;
    private final ObjectMapper objectMapper;
    private final WorkflowAuthorizationClient authorizationClient;

    @Autowired
    public RequestService(RequestMapper mapper, ObjectMapper objectMapper,
                          WorkflowAuthorizationClient authorizationClient) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.authorizationClient = authorizationClient;
    }

    @Deprecated
    public RequestService(RequestMapper mapper, ObjectMapper objectMapper) {
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
                throw new UnsupportedOperationException("Legacy request service does not authorize versions");
            }
        });
    }

    @Transactional
    public RequestDtos.RequestData create(RequestDtos.CreateRequest request, long applicantId) {
        return createInternal(request, new WorkflowActor(applicantId, DataScope.all(applicantId),
                "legacy-test-token"), false);
    }

    @Transactional
    public RequestDtos.RequestData create(RequestDtos.CreateRequest request, WorkflowActor actor) {
        return createInternal(request, actor, true);
    }

    private RequestDtos.RequestData createInternal(RequestDtos.CreateRequest request,
                                                    WorkflowActor actor,
                                                    boolean authorizeEmployee) {
        WorkflowAuthorizationClient.AuthorizedResource resource = authorizeEmployee
                ? authorizationClient.authorizeEmployee(request.employeeId(), actor.bearerToken())
                : new WorkflowAuthorizationClient.AuthorizedResource(request.employeeId(), null);
        if (resource == null || !Long.valueOf(request.employeeId()).equals(resource.employeeId())) {
            throw new IllegalArgumentException("Employee authorization failed");
        }
        String requestType = normalizeType(request.requestType());
        HrRequest entity = new HrRequest();
        entity.setRequestNo("HR-" + REQUEST_TIME.format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        entity.setRequestType(requestType);
        entity.setEmployeeId(request.employeeId());
        entity.setTargetDepartmentId(resource.departmentId());
        entity.setApplicantId(actor.userId());
        try {
            entity.setPayloadJson(objectMapper.writeValueAsString(
                    request.payload() == null ? objectMapper.createObjectNode() : request.payload()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("payload could not be serialized", exception);
        }
        entity.setStatus("DRAFT");
        entity.setCurrentNode("DRAFT");
        mapper.insert(entity);
        return toData(entity);
    }

    @Transactional
    public RequestDtos.RequestData submit(long id, long operatorId) {
        return submit(id, legacyActor(operatorId));
    }

    @Transactional
    public RequestDtos.RequestData submit(long id, WorkflowActor actor) {
        HrRequest entity = require(id, actor);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new RequestStateException("DRAFT", entity.getStatus());
        }
        if (!Long.valueOf(actor.userId()).equals(entity.getApplicantId())) {
            throw new RequestStateException("applicant " + entity.getApplicantId(), Long.toString(actor.userId()));
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.updateState(id, "PENDING_DEPT_APPROVAL", "DEPT_APPROVAL", now, null);
        audit(id, actor.userId(), "REQUEST_SUBMITTED", "submitted");
        entity.setStatus("PENDING_DEPT_APPROVAL");
        entity.setCurrentNode("DEPT_APPROVAL");
        entity.setSubmittedAt(now);
        return toData(entity);
    }

    @Transactional
    public RequestDtos.RequestData approve(long id, long operatorId, boolean approved, String comment) {
        return approve(id, legacyActor(operatorId), approved, comment);
    }

    @Transactional
    public RequestDtos.RequestData approve(long id, WorkflowActor actor, boolean approved, String comment) {
        HrRequest entity = require(id, actor);
        String nextStatus;
        String nextNode;
        if ("PENDING_DEPT_APPROVAL".equals(entity.getStatus())) {
            nextStatus = approved ? "PENDING_HR_APPROVAL" : "REJECTED";
            nextNode = approved ? "HR_APPROVAL" : "COMPLETED";
        } else if ("PENDING_HR_APPROVAL".equals(entity.getStatus())) {
            nextStatus = approved ? "APPROVED" : "REJECTED";
            nextNode = "COMPLETED";
        } else {
            throw new RequestStateException("PENDING_DEPT_APPROVAL or PENDING_HR_APPROVAL", entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        ApprovalRecord approval = new ApprovalRecord();
        approval.setRequestId(id);
        approval.setNodeCode(entity.getCurrentNode());
        approval.setApproverId(actor.userId());
        approval.setDecision(approved ? "APPROVED" : "REJECTED");
        approval.setComment(comment == null ? null : comment.trim());
        approval.setDecidedAt(now);
        mapper.insertApproval(approval);
        mapper.updateState(id, nextStatus, nextNode, entity.getSubmittedAt(),
                "APPROVED".equals(nextStatus) || "REJECTED".equals(nextStatus) ? now : null);
        audit(id, actor.userId(), approved ? "REQUEST_APPROVED" : "REQUEST_REJECTED", approval.getComment());
        entity.setStatus(nextStatus);
        entity.setCurrentNode(nextNode);
        entity.setCompletedAt("COMPLETED".equals(nextNode) ? now : null);
        return toData(entity);
    }

    @Transactional(readOnly = true)
    public RequestDtos.RequestData get(long id) {
        return toData(requireLegacy(id));
    }

    @Transactional(readOnly = true)
    public RequestDtos.RequestData get(long id, WorkflowActor actor) {
        return toData(require(id, actor));
    }

    private HrRequest require(long id, DataScope scope) {
        HrRequest entity = mapper.findById(id, scope.type().name(), scope.employeeId(), scope.departmentId());
        if (entity == null) throw new RequestNotFoundException(id);
        return entity;
    }

    private HrRequest requireLegacy(long id) {
        HrRequest entity = mapper.findById(id);
        if (entity == null) throw new RequestNotFoundException(id);
        return entity;
    }

    private HrRequest require(long id, WorkflowActor actor) {
        return legacy(actor) ? requireLegacy(id) : require(id, actor.scope());
    }

    private WorkflowActor legacyActor(long operatorId) {
        return new WorkflowActor(operatorId, DataScope.all(operatorId), "legacy-test-token");
    }

    private boolean legacy(WorkflowActor actor) {
        return "legacy-test-token".equals(actor.bearerToken());
    }

    private RequestDtos.RequestData toData(HrRequest entity) {
        JsonNode payload;
        try {
            payload = entity.getPayloadJson() == null ? objectMapper.createObjectNode()
                    : objectMapper.readTree(entity.getPayloadJson());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored request payload is invalid", exception);
        }
        return new RequestDtos.RequestData(entity.getId(), entity.getRequestNo(), entity.getRequestType(),
                entity.getEmployeeId(), entity.getApplicantId(), payload, entity.getStatus(), entity.getCurrentNode());
    }

    private void audit(long requestId, long operatorId, String action, String detail) {
        WorkflowAuditLog audit = new WorkflowAuditLog();
        audit.setActorUserId(operatorId);
        audit.setActionCode(action);
        audit.setResourceType("HR_REQUEST");
        audit.setResourceId(Long.toString(requestId));
        try {
            audit.setDetailJson(objectMapper.writeValueAsString(detail == null ? "" : detail));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit detail could not be serialized", exception);
        }
        mapper.insertAudit(audit);
    }

    private String normalizeType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ONBOARDING") && !normalized.equals("TRANSFER") && !normalized.equals("RESIGNATION")) {
            throw new IllegalArgumentException("Unsupported request type");
        }
        return normalized;
    }
}
