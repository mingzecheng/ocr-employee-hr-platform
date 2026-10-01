package com.hrplatform.inventory;

import com.hrplatform.archive.ArchiveMapper;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.common.security.DataScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {
    private final InventoryMapper mapper;
    private final ArchiveMapper archiveMapper;

    @Autowired
    public InventoryService(InventoryMapper mapper, ArchiveMapper archiveMapper) {
        this.mapper = mapper;
        this.archiveMapper = archiveMapper;
    }

    public InventoryService(InventoryMapper mapper) {
        this(mapper, null);
    }

    @Transactional
    public InventoryTask create(InventoryCreateRequest request, InventoryActor actor) {
        if (actor.scope().type() == DataScope.Type.NONE || archiveMapper == null) {
            throw new InventoryStateException("当前用户没有盘点范围");
        }
        InventoryTask task = new InventoryTask(null,
                "INV-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                        + "-" + UUID.randomUUID().toString().substring(0, 8), "DRAFT", actor.userId());
        InventoryTask saved = mapper.insertTask(task);
        InventoryTask result = saved == null ? task : saved;
        for (Long versionId : request.versionIds()) {
            ArchiveOcrSource source = archiveMapper.findOcrSourceByVersionIdWithScope(versionId,
                    actor.scope().employeeId(), actor.scope().departmentId(), actor.scope().type().name());
            if (source == null) {
                throw new InventoryStateException("档案版本不存在或无权纳入盘点: " + versionId);
            }
            mapper.insertItem(new InventoryItem(null, result.id(), versionId, null, source.employeeId(),
                    source.departmentId(), null, null, null, null));
        }
        return result;
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
        if (!"DRAFT".equals(task.status())) {
            throw new InventoryStateException("盘点任务只能从草稿开始");
        }
        InventoryTask started = new InventoryTask(task.id(), task.taskNo(), "IN_PROGRESS", task.initiatorId());
        if (mapper.updateTask(started) != 1) {
            throw new InventoryStateException("盘点任务版本已变化，请刷新后重试");
        }
        return started;
    }

    @Transactional
    public InventoryTask complete(Long id, InventoryActor actor) {
        InventoryTask task = require(id, actor);
        return complete(task, mapper.listItems(id));
    }

    @Transactional
    public InventoryItem resolve(Long taskId, Long itemId, String resolution, InventoryActor actor) {
        InventoryTask task = require(taskId, actor);
        if (!"ABNORMAL".equals(task.status()) && !"IN_PROGRESS".equals(task.status())) {
            throw new InventoryStateException("当前任务不允许处理差异");
        }
        List<InventoryItem> items = mapper.listItems(taskId);
        InventoryItem item = items.stream().filter(value -> value.id().equals(itemId)).findFirst()
                .orElseThrow(() -> new InventoryStateException("盘点项目不存在: " + itemId));
        InventoryItem resolved = new InventoryItem(item.id(), item.taskId(), item.expectedVersionId(),
                item.expectedVersionId(), item.employeeId(), item.departmentId(), "PRESENT", "NONE",
                resolution, actor.userId());
        if (mapper.updateItem(resolved) != 1) {
            throw new InventoryStateException("盘点项目已被其他人更新");
        }
        return resolved;
    }

    private InventoryTask require(Long id, InventoryActor actor) {
        InventoryTask task = mapper.findTask(id, actor.scope().departmentId(), actor.scope().type().name());
        if (task == null) {
            throw new InventoryStateException("盘点任务不存在或无权访问: " + id);
        }
        return task;
    }

    @Transactional
    public InventoryTask complete(InventoryTask task, List<InventoryItem> items) {
        if (!"IN_PROGRESS".equals(task.status())) {
            throw new InventoryStateException("盘点任务必须处于进行中状态");
        }
        if (items == null || items.isEmpty() || items.stream().anyMatch(item -> item.actualStatus() == null)) {
            throw new InventoryStateException("仍有未盘点项目");
        }
        if (items.stream().anyMatch(item -> item.differenceType() != null
                && !"NONE".equals(item.differenceType()))) {
            throw new InventoryStateException("存在未解决的盘点差异");
        }
        InventoryTask completed = new InventoryTask(task.id(), task.taskNo(), "COMPLETED", task.initiatorId());
        if (mapper != null && mapper.updateTask(completed) != 1) {
            throw new InventoryStateException("盘点任务版本已变化，请刷新后重试");
        }
        return completed;
    }
}
