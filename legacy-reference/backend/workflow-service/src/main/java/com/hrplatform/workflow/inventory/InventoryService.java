package com.hrplatform.workflow.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.workflow.authorization.WorkflowAuthorizationClient;
import com.hrplatform.workflow.authorization.WorkflowDataScopeDeniedException;
import com.hrplatform.workflow.request.WorkflowAuditLog;
import com.hrplatform.workflow.security.WorkflowActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class InventoryService {
    private static final DateTimeFormatter TASK_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final InventoryMapper mapper;
    private final ObjectMapper objectMapper;
    private final WorkflowAuthorizationClient authorizationClient;

    @Autowired
    public InventoryService(InventoryMapper mapper, ObjectMapper objectMapper,
                            WorkflowAuthorizationClient authorizationClient) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.authorizationClient = authorizationClient;
    }

    @Deprecated
    public InventoryService(InventoryMapper mapper, ObjectMapper objectMapper) {
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
    public InventoryDtos.TaskData create(InventoryDtos.CreateRequest request, long initiatorId) {
        return createInternal(request, legacyActor(initiatorId), false);
    }

    @Transactional
    public InventoryDtos.TaskData create(InventoryDtos.CreateRequest request, WorkflowActor actor) {
        return createInternal(request, actor, true);
    }

    private InventoryDtos.TaskData createInternal(InventoryDtos.CreateRequest request,
                                                  WorkflowActor actor,
                                                  boolean authorizeItems) {
        String scopeType = normalizeScopeType(request.scopeType());
        InventoryTask task = new InventoryTask();
        task.setTaskNo("INV-" + TASK_TIME.format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        task.setScopeType(scopeType);
        task.setScopeValue(blankToNull(request.scopeValue()));
        task.setInitiatorId(actor.userId());
        task.setStatus("DRAFT");
        mapper.insertTask(task);
        for (InventoryDtos.ItemRequest requestItem : request.items()) {
            WorkflowAuthorizationClient.AuthorizedResource resource = authorizeItems
                    ? authorizationClient.authorizeVersion(requestItem.expectedVersionId(), actor.bearerToken())
                    : null;
            if (authorizeItems && (resource == null || resource.employeeId() <= 0
                    || !Long.valueOf(requestItem.archiveDocumentId()).equals(resource.documentId()))) {
                throw new WorkflowDataScopeDeniedException();
            }
            InventoryItem item = new InventoryItem();
            item.setTaskId(task.getId());
            item.setArchiveDocumentId(requestItem.archiveDocumentId());
            if (resource != null) {
                item.setEmployeeId(resource.employeeId());
                item.setDepartmentId(resource.departmentId());
            }
            item.setExpectedVersionId(requestItem.expectedVersionId());
            mapper.insertItem(item);
        }
        audit(task.getId(), actor.userId(), "INVENTORY_CREATED", scopeType);
        return toData(task);
    }

    @Transactional
    public InventoryDtos.TaskData start(long id, long operatorId) {
        return start(id, legacyActor(operatorId));
    }

    @Transactional
    public InventoryDtos.TaskData start(long id, WorkflowActor actor) {
        InventoryTask task = requireTask(id, actor);
        requireState(task, "DRAFT");
        LocalDateTime now = LocalDateTime.now();
        mapper.updateTaskState(id, "IN_PROGRESS", now, null);
        audit(id, actor.userId(), "INVENTORY_STARTED", "started");
        task.setStatus("IN_PROGRESS");
        task.setStartedAt(now);
        return toData(task, actor);
    }

    @Transactional
    public InventoryDtos.ItemData checkItem(long taskId, long itemId, long operatorId,
                                            InventoryDtos.CheckRequest request) {
        return checkItem(taskId, itemId, legacyActor(operatorId), request);
    }

    @Transactional
    public InventoryDtos.ItemData checkItem(long taskId, long itemId, WorkflowActor actor,
                                            InventoryDtos.CheckRequest request) {
        InventoryTask task = requireTask(taskId, actor);
        requireState(task, "IN_PROGRESS");
        InventoryItem item = legacy(actor)
                ? mapper.findItemById(itemId)
                : mapper.findItemById(itemId, actor.scope().type().name(), actor.scope().employeeId(),
                actor.scope().departmentId());
        if (item == null || !Long.valueOf(taskId).equals(item.getTaskId())) {
            throw new InventoryNotFoundException("Inventory item", itemId);
        }
        if (item.getCheckedAt() != null) {
            throw new InventoryStateException("unchecked item", "already checked item");
        }
        String actualStatus = normalizeActualStatus(request.actualStatus());
        Long actualVersionId = request.actualVersionId();
        if ("PRESENT".equals(actualStatus) && actualVersionId == null) {
            throw new IllegalArgumentException("actualVersionId is required when actualStatus is PRESENT");
        }
        String differenceType = differenceType(item, actualVersionId, actualStatus);
        String description = blankToNull(request.differenceDescription());
        if (!"NONE".equals(differenceType) && description == null) {
            throw new IllegalArgumentException("differenceDescription is required for an abnormal item");
        }
        LocalDateTime now = LocalDateTime.now();
        item.setActualVersionId(actualVersionId);
        item.setActualStatus(actualStatus);
        item.setDifferenceType(differenceType);
        item.setDifferenceDescription(description);
        item.setCheckedBy(actor.userId());
        item.setCheckedAt(now);
        mapper.updateItemCheck(item);
        audit(taskId, actor.userId(), "INVENTORY_ITEM_CHECKED", differenceType);
        return toItemData(item);
    }

    @Transactional
    public InventoryDtos.TaskData complete(long id, long operatorId) {
        return complete(id, legacyActor(operatorId));
    }

    @Transactional
    public InventoryDtos.TaskData complete(long id, WorkflowActor actor) {
        InventoryTask task = requireTask(id, actor);
        requireState(task, "IN_PROGRESS");
        ensureEntireTaskInScope(id, actor.scope());
        List<InventoryItem> items = items(id, actor);
        if (items.isEmpty() || items.stream().anyMatch(item -> item.getCheckedAt() == null)) {
            throw new InventoryStateException("all items checked", "unchecked items");
        }
        boolean abnormal = items.stream().anyMatch(item -> !"NONE".equals(item.getDifferenceType()));
        String status = abnormal ? "ABNORMAL" : "COMPLETED";
        LocalDateTime now = LocalDateTime.now();
        mapper.updateTaskState(id, status, task.getStartedAt(), now);
        audit(id, actor.userId(), abnormal ? "INVENTORY_ABNORMAL" : "INVENTORY_COMPLETED", status);
        task.setStatus(status);
        task.setCompletedAt(now);
        return toData(task, actor);
    }

    @Transactional
    public InventoryDtos.TaskData resolve(long id, long operatorId, InventoryDtos.ResolveRequest request) {
        return resolve(id, legacyActor(operatorId), request);
    }

    @Transactional
    public InventoryDtos.TaskData resolve(long id, WorkflowActor actor, InventoryDtos.ResolveRequest request) {
        InventoryTask task = requireTask(id, actor);
        requireState(task, "ABNORMAL");
        ensureEntireTaskInScope(id, actor.scope());
        InventoryResolution resolution = new InventoryResolution();
        resolution.setTaskId(id);
        resolution.setOperatorId(actor.userId());
        resolution.setResolution(request.resolution().trim());
        resolution.setCreatedAt(LocalDateTime.now());
        mapper.insertResolution(resolution);
        LocalDateTime now = LocalDateTime.now();
        mapper.updateTaskState(id, "COMPLETED", task.getStartedAt(), now);
        audit(id, actor.userId(), "INVENTORY_RESOLVED", resolution.getResolution());
        task.setStatus("COMPLETED");
        task.setCompletedAt(now);
        return toData(task, actor);
    }

    @Transactional(readOnly = true)
    public InventoryDtos.TaskData get(long id) {
        return toData(requireTask(id, legacyActor(0L)));
    }

    @Transactional(readOnly = true)
    public InventoryDtos.TaskData get(long id, WorkflowActor actor) {
        return toData(requireTask(id, actor), actor);
    }

    private InventoryTask requireTask(long id, WorkflowActor actor) {
        InventoryTask task = legacy(actor)
                ? mapper.findTaskById(id)
                : mapper.findTaskById(id, actor.scope().type().name(), actor.scope().employeeId(),
                actor.scope().departmentId());
        if (task == null) {
            throw new InventoryNotFoundException("Inventory task", id);
        }
        return task;
    }

    private void requireState(InventoryTask task, String expected) {
        if (!expected.equals(task.getStatus())) {
            throw new InventoryStateException(expected, task.getStatus());
        }
    }

    private InventoryDtos.TaskData toData(InventoryTask task) {
        return toData(task, legacyActor(task.getInitiatorId() == null ? 0L : task.getInitiatorId()));
    }

    private InventoryDtos.TaskData toData(InventoryTask task, WorkflowActor actor) {
        List<InventoryItem> visibleItems = items(task.getId(), actor);
        List<InventoryDtos.ItemData> items = (visibleItems == null ? List.<InventoryItem>of() : visibleItems).stream()
                .map(this::toItemData).toList();
        List<InventoryDtos.ResolutionData> resolutions = mapper.findResolutionByTaskId(task.getId()).stream()
                .map(resolution -> new InventoryDtos.ResolutionData(resolution.getId(), resolution.getOperatorId(),
                        resolution.getResolution(), resolution.getCreatedAt())).toList();
        return new InventoryDtos.TaskData(task.getId(), task.getTaskNo(), task.getScopeType(), task.getScopeValue(),
                task.getInitiatorId(), task.getStatus(), task.getStartedAt(), task.getCompletedAt(), items, resolutions);
    }

    private List<InventoryItem> items(long taskId, WorkflowActor actor) {
        return legacy(actor) ? mapper.findItems(taskId)
                : mapper.findItems(taskId, actor.scope().type().name(), actor.scope().employeeId(),
                actor.scope().departmentId());
    }

    private void ensureEntireTaskInScope(long taskId, DataScope scope) {
        if (mapper.countItems(taskId) != mapper.countItemsInScope(taskId, scope.type().name(),
                scope.employeeId(), scope.departmentId())) {
            throw new WorkflowDataScopeDeniedException();
        }
    }

    private InventoryDtos.ItemData toItemData(InventoryItem item) {
        return new InventoryDtos.ItemData(item.getId(), item.getArchiveDocumentId(), item.getExpectedVersionId(),
                item.getActualVersionId(), item.getActualStatus(), item.getDifferenceType(),
                item.getDifferenceDescription(), item.getCheckedBy(), item.getCheckedAt());
    }

    private String differenceType(InventoryItem item, Long actualVersionId, String actualStatus) {
        if ("MISSING".equals(actualStatus)) return "MISSING";
        if ("DAMAGED".equals(actualStatus)) return "DAMAGED";
        if (!item.getExpectedVersionId().equals(actualVersionId)) return "VERSION_MISMATCH";
        return "NONE";
    }

    private void audit(long taskId, long operatorId, String action, String detail) {
        WorkflowAuditLog audit = new WorkflowAuditLog();
        audit.setActorUserId(operatorId);
        audit.setActionCode(action);
        audit.setResourceType("INVENTORY_TASK");
        audit.setResourceId(Long.toString(taskId));
        try {
            audit.setDetailJson(objectMapper.writeValueAsString(detail == null ? "" : detail));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit detail could not be serialized", exception);
        }
        mapper.insertAudit(audit);
    }

    private String normalizeScopeType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("EMPLOYEE", "DEPARTMENT", "DOCUMENT_TYPE", "ALL").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported inventory scope type");
        }
        return normalized;
    }

    private String normalizeActualStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PRESENT", "MISSING", "DAMAGED").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported inventory actual status");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private WorkflowActor legacyActor(long operatorId) {
        return new WorkflowActor(operatorId, DataScope.all(operatorId), "legacy-test-token");
    }

    private boolean legacy(WorkflowActor actor) {
        return "legacy-test-token".equals(actor.bearerToken());
    }
}
