package com.hrplatform.inventory;

import com.hrplatform.archive.ArchiveCatalog;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.archive.ArchiveVersion;
import com.hrplatform.audit.OperationLogService;
import com.hrplatform.common.security.DataScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class InventoryService {
    private final InventoryMapper mapper;
    private final ArchiveCatalog archiveCatalog;
    private final OperationLogService operationLogService;

    @Autowired
    public InventoryService(InventoryMapper mapper, ArchiveCatalog archiveCatalog,
                            OperationLogService operationLogService) {
        this.mapper = mapper;
        this.archiveCatalog = archiveCatalog;
        this.operationLogService = operationLogService;
    }

    public InventoryService(InventoryMapper mapper, ArchiveCatalog archiveCatalog) {
        this(mapper, archiveCatalog, null);
    }

    public InventoryService(InventoryMapper mapper) {
        this(mapper, null, null);
    }

    @Transactional
    public InventoryTask create(InventoryCreateRequest request, InventoryActor actor) {
        validateCreateRequest(request);
        if (actor.scope().type() == DataScope.Type.NONE || archiveCatalog == null) {
            throw new InventoryStateException("当前用户没有盘点范围");
        }
        InventoryTask task = new InventoryTask(null,
                "INV-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                        + "-" + UUID.randomUUID().toString().substring(0, 8), "DRAFT", actor.userId());
        if (mapper.insertTask(task) != 1) {
            throw new InventoryStateException("盘点任务创建失败");
        }
        InventoryTask result = mapper.findTaskByTaskNo(task.taskNo());
        if (result == null) {
            throw new InventoryStateException("盘点任务已写入但无法读取");
        }
        for (Long versionId : request.versionIds()) {
            ArchiveOcrSource source = archiveCatalog.findOcrSourceByVersionIdWithScope(versionId,
                    actor.scope().employeeId(), actor.scope().departmentId(), actor.scope().type().name());
            if (source == null) {
                throw new InventoryStateException("档案版本不存在或无权纳入盘点: " + versionId);
            }
            if (mapper.insertItem(new InventoryItem(null, result.id(), versionId, null, null, source.employeeId(),
                    source.departmentId(), null, null, null, null, "PENDING", null)) != 1) {
                throw new InventoryStateException("盘点项目创建失败: " + versionId);
            }
        }
        record(actor.userId(), "CREATE", result.id());
        return result;
    }

    private void validateCreateRequest(InventoryCreateRequest request) {
        if (request == null || request.versionIds() == null || request.versionIds().isEmpty()) {
            throw new IllegalArgumentException("至少选择一个档案版本");
        }
        if (request.versionIds().size() > 200) {
            throw new IllegalArgumentException("单个盘点任务最多包含 200 个档案版本");
        }
        if (request.versionIds().stream().anyMatch(versionId -> versionId == null || versionId <= 0)) {
            throw new IllegalArgumentException("档案版本 ID 必须为正数");
        }
        if (new HashSet<>(request.versionIds()).size() != request.versionIds().size()) {
            throw new IllegalArgumentException("盘点范围不能包含重复档案版本");
        }
    }

    @Transactional(readOnly = true)
    public InventoryPage list(int page, int pageSize, InventoryActor actor) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        DataScope scope = actor.scope();
        Long employeeId = scope.type() == DataScope.Type.EMPLOYEE ? scope.employeeId() : null;
        Long departmentId = scope.type() == DataScope.Type.DEPARTMENT ? scope.departmentId() : null;
        return new InventoryPage(mapper.listTasks(departmentId, employeeId, scope.type().name(),
                (safePage - 1) * safeSize, safeSize),
                mapper.countTasks(departmentId, employeeId, scope.type().name()), safePage, safeSize);
    }

    @Transactional
    public InventoryTask start(Long id, InventoryActor actor) {
        InventoryTask task = require(id, actor);
        requireCompleteDepartmentScope(mapper.listAllItems(id), actor);
        if (!"DRAFT".equals(task.status())) {
            throw new InventoryStateException("盘点任务只能从草稿开始");
        }
        InventoryTask started = new InventoryTask(task.id(), task.taskNo(), "IN_PROGRESS", task.initiatorId());
        if (mapper.transitionTask(id, "DRAFT", "IN_PROGRESS") != 1) {
            throw new InventoryStateException("盘点任务版本已变化，请刷新后重试");
        }
        record(actor.userId(), "START", id);
        return started;
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> listItems(Long taskId, InventoryActor actor) {
        require(taskId, actor);
        DataScope scope = actor.scope();
        return mapper.listItems(taskId, scope.departmentId(), scope.employeeId(), scope.type().name());
    }

    @Transactional(readOnly = true)
    public List<InventoryVersionOption> availableVersions(Long taskId, Long itemId, InventoryActor actor) {
        require(taskId, actor);
        InventoryItem item = requireItem(taskId, itemId, actor);
        ArchiveVersion expected = scopedVersion(item.expectedVersionId(), actor.scope());
        if (expected == null) {
            throw new InventoryStateException("盘点目标档案版本不存在或无权访问");
        }
        DataScope scope = actor.scope();
        return archiveCatalog.listVersions(expected.documentId(), scope.employeeId(), scope.departmentId(),
                        scope.type().name(), 0, 100)
                .stream()
                .map(version -> new InventoryVersionOption(version.id(), version.versionNo(),
                        version.status(), version.isCurrent()))
                .toList();
    }

    @Transactional
    public InventoryItem checkItem(Long taskId, Long itemId, InventoryCheckRequest request,
                                   InventoryActor actor) {
        InventoryTask task = require(taskId, actor);
        if (!"IN_PROGRESS".equals(task.status())) {
            throw new InventoryStateException("只能登记进行中盘点任务的实际状态");
        }
        InventoryItem item = requireItem(taskId, itemId, actor);
        if (!"PENDING".equals(item.resolutionStatus()) || item.actualStatus() != null) {
            throw new InventoryStateException("该盘点项目已登记实际状态");
        }

        String actualStatus = request.actualStatus().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PRESENT", "MISSING", "DAMAGED").contains(actualStatus)) {
            throw new IllegalArgumentException("actualStatus 仅支持 PRESENT、MISSING 或 DAMAGED");
        }
        Long actualVersionId = request.actualVersionId();
        if ("MISSING".equals(actualStatus)) {
            if (actualVersionId != null) {
                throw new IllegalArgumentException("MISSING 状态不能填写 actualVersionId");
            }
            actualVersionId = null;
        } else {
            if ("DAMAGED".equals(actualStatus) && actualVersionId == null) {
                actualVersionId = item.expectedVersionId();
            }
            ArchiveVersion expected = scopedVersion(item.expectedVersionId(), actor.scope());
            ArchiveVersion actual = actualVersionId == null ? null : scopedVersion(actualVersionId, actor.scope());
            if (expected == null || actual == null) {
                throw new InventoryStateException("实际档案版本不存在或无权访问");
            }
            if (!expected.documentId().equals(actual.documentId())) {
                throw new InventoryStateException("现场版本必须属于同一份档案材料");
            }
        }

        String differenceType = switch (actualStatus) {
            case "MISSING" -> "MISSING";
            case "DAMAGED" -> "DAMAGED";
            default -> item.expectedVersionId().equals(actualVersionId) ? "NONE" : "VERSION_MISMATCH";
        };
        String description = request.note() == null || request.note().isBlank()
                ? defaultDescription(differenceType) : request.note().trim();
        String resolutionStatus = "NONE".equals(differenceType) ? "NOT_REQUIRED" : "OPEN";
        InventoryItem checked = new InventoryItem(item.id(), item.taskId(), item.expectedVersionId(),
                item.expectedVersionNo(), actualVersionId,
                item.employeeId(), item.departmentId(), actualStatus, differenceType, description,
                actor.userId(), resolutionStatus, null, item.employeeNo(), item.employeeName(), item.documentTitle());
        if (mapper.recordCheck(checked) != 1) {
            throw new InventoryStateException("盘点项目已被其他人更新");
        }
        if (!"NONE".equals(differenceType)
                && mapper.insertExceptionRecord(itemId, differenceType, description) != 1) {
            throw new InventoryStateException("盘点异常记录创建失败");
        }
        record(actor.userId(), "CHECK_ITEM", "INVENTORY_ITEM", itemId);
        return checked;
    }

    @Transactional
    public InventoryTask complete(Long id, InventoryActor actor) {
        InventoryTask task = require(id, actor);
        List<InventoryItem> items = mapper.listAllItems(id);
        requireCompleteDepartmentScope(items, actor);
        InventoryTask completed = complete(task, items);
        record(actor.userId(), "COMPLETE", id);
        return completed;
    }

    @Transactional
    public InventoryItem resolve(Long taskId, Long itemId, String resolution, InventoryActor actor) {
        InventoryTask task = require(taskId, actor);
        if (!"ABNORMAL".equals(task.status()) && !"IN_PROGRESS".equals(task.status())) {
            throw new InventoryStateException("当前任务不允许处理差异");
        }
        InventoryItem item = requireItem(taskId, itemId, actor);
        if (!"OPEN".equals(item.resolutionStatus())) {
            throw new InventoryStateException("该盘点项目没有待处理异常");
        }
        String reason = resolution == null ? "" : resolution.trim();
        if (reason.isBlank()) {
            throw new IllegalArgumentException("resolution 不能为空");
        }
        if (mapper.resolveExceptionRecord(itemId, actor.userId(), reason) < 1
                || mapper.updateResolutionStatus(itemId, "RESOLVED") != 1) {
            throw new InventoryStateException("盘点项目已被其他人更新");
        }
        InventoryItem resolved = new InventoryItem(item.id(), item.taskId(), item.expectedVersionId(),
                item.expectedVersionNo(),
                item.actualVersionId(), item.employeeId(), item.departmentId(), item.actualStatus(),
                item.differenceType(), item.differenceDescription(), item.checkedBy(), "RESOLVED", reason,
                item.employeeNo(), item.employeeName(), item.documentTitle());
        record(actor.userId(), "RESOLVE_ITEM", "INVENTORY_ITEM", itemId);
        return resolved;
    }

    private InventoryTask require(Long id, InventoryActor actor) {
        InventoryTask task = mapper.findTask(id, actor.scope().departmentId(), actor.scope().employeeId(),
                actor.scope().type().name());
        if (task == null) {
            throw new InventoryStateException("盘点任务不存在或无权访问: " + id);
        }
        return task;
    }

    private InventoryItem requireItem(Long taskId, Long itemId, InventoryActor actor) {
        DataScope scope = actor.scope();
        InventoryItem item = mapper.findItem(taskId, itemId, scope.departmentId(),
                scope.employeeId(), scope.type().name());
        if (item == null) {
            throw new InventoryStateException("盘点项目不存在: " + itemId);
        }
        return item;
    }

    private void requireCompleteDepartmentScope(List<InventoryItem> items, InventoryActor actor) {
        if (actor.scope().type() != DataScope.Type.DEPARTMENT) {
            return;
        }
        Long departmentId = actor.scope().departmentId();
        if (departmentId == null || items == null || items.isEmpty()
                || items.stream().anyMatch(item -> item == null || !departmentId.equals(item.departmentId()))) {
            throw new InventoryStateException("盘点任务的完整范围必须全部属于本部门");
        }
    }

    private ArchiveVersion scopedVersion(Long versionId, DataScope scope) {
        if (archiveCatalog == null || versionId == null) {
            return null;
        }
        return archiveCatalog.findVersionByIdWithScope(versionId, scope.employeeId(),
                scope.departmentId(), scope.type().name());
    }

    private String defaultDescription(String differenceType) {
        return switch (differenceType) {
            case "MISSING" -> "现场未找到档案材料";
            case "DAMAGED" -> "现场发现档案材料损坏";
            case "VERSION_MISMATCH" -> "现场版本与档案版本不一致";
            default -> "现场版本与档案版本一致";
        };
    }

    private void record(Long operatorId, String action, Long objectId) {
        record(operatorId, action, "INVENTORY_TASK", objectId);
    }

    private void record(Long operatorId, String action, String objectType, Long objectId) {
        if (operationLogService != null) {
            operationLogService.record(operatorId, action, objectType, objectId, "SUCCESS");
        }
    }

    @Transactional
    public InventoryTask complete(InventoryTask task, List<InventoryItem> items) {
        if (!"IN_PROGRESS".equals(task.status())) {
            throw new InventoryStateException("盘点任务必须处于进行中状态");
        }
        if (items == null || items.isEmpty() || items.stream().anyMatch(item -> item.actualStatus() == null
                || "PENDING".equals(item.resolutionStatus()))) {
            throw new InventoryStateException("仍有未盘点项目");
        }
        if (items.stream().anyMatch(item -> "OPEN".equals(item.resolutionStatus()))) {
            throw new InventoryStateException("存在未解决的盘点差异");
        }
        InventoryTask completed = new InventoryTask(task.id(), task.taskNo(), "COMPLETED", task.initiatorId());
        if (mapper != null && mapper.transitionTask(task.id(), "IN_PROGRESS", "COMPLETED") != 1) {
            throw new InventoryStateException("盘点任务版本已变化，请刷新后重试");
        }
        return completed;
    }
}
