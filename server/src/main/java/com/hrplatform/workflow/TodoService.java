package com.hrplatform.workflow;

import com.hrplatform.common.security.DataScope;
import com.hrplatform.contract.ContractReminderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TodoService {
    private static final int MAX_LIMIT = 100;
    private final TodoMapper mapper;
    private final ContractReminderMapper reminderMapper;

    public TodoService(TodoMapper mapper, ContractReminderMapper reminderMapper) {
        this.mapper = mapper;
        this.reminderMapper = reminderMapper;
    }

    @Transactional(readOnly = true)
    public TodoData list(RequestActor actor, int limit) {
        int safeLimit = Math.min(MAX_LIMIT, Math.max(1, limit));
        LocalDateTime now = LocalDateTime.now();
        DataScope scope = actor.scope();
        if (scope.type() == DataScope.Type.NONE) {
            return data(List.of(), now);
        }

        List<TodoItem> items = new ArrayList<>();
        boolean allowDepartmentApproval = actor.roles().contains("DEPT_MANAGER");
        boolean allowHrApproval = actor.roles().contains("HR_ADMIN");
        mapper.findPendingHrRequests(scope.type().name(), scope.employeeId(), scope.departmentId(),
                        allowDepartmentApproval, allowHrApproval, MAX_LIMIT)
                .forEach(row -> items.add(map(row, "HR_REQUEST_APPROVAL", "HIGH",
                        "/todos/hr-request/" + row.resourceId())));
        mapper.findPendingArchiveAccess(scope.type().name(), scope.employeeId(), scope.departmentId(),
                        allowHrApproval, MAX_LIMIT)
                .forEach(row -> items.add(map(row, "ARCHIVE_ACCESS_APPROVAL", "HIGH",
                        "/todos/archive-access/" + row.resourceId())));
        mapper.findDueUses(scope.type().name(), scope.employeeId(), scope.departmentId(), now,
                        now.plusDays(3), MAX_LIMIT)
                .forEach(row -> items.add(map(row, "ARCHIVE_RETURN_DUE",
                        "ABNORMAL".equals(row.status()) || row.dueAt() != null && row.dueAt().isBefore(now)
                                ? "HIGH" : "MEDIUM",
                        "/todos/archive-return/" + row.resourceId())));
        if (allowHrApproval) {
            mapper.findFailedOcr(scope.type().name(), scope.employeeId(), scope.departmentId(), MAX_LIMIT)
                    .forEach(row -> items.add(map(row, "OCR_FAILED", "MEDIUM",
                            "/todos/ocr/" + row.resourceId())));
        }
        // 合同到期提醒：HR(ALL) 与部门负责人(DEPARTMENT)可见，普通员工 scope 由 SQL 置空。
        reminderMapper.findPendingForTodo(scope.type().name(), scope.employeeId(), scope.departmentId(),
                        MAX_LIMIT)
                .forEach(row -> items.add(new TodoItem(row.id(), "CONTRACT_RENEWAL_DUE", row.title(),
                        row.resourceId(), row.status(), reminderPriority(row.dueAt(), now),
                        row.dueAt(), row.createdAt(),
                        "/todos/contract-reminder/" + row.resourceId())));

        items.sort(Comparator.comparingInt((TodoItem item) -> priority(item.priority()))
                .thenComparing(TodoItem::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItem::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(TodoItem::id, Comparator.nullsLast(Comparator.reverseOrder())));
        return data(items.size() > safeLimit ? new ArrayList<>(items.subList(0, safeLimit)) : items, now);
    }

    private TodoItem map(TodoMapper.TodoRow row, String type, String priority, String targetPath) {
        return new TodoItem(row.id(), type, row.title(), row.resourceId(), row.status(), priority,
                row.dueAt(), row.createdAt(), targetPath);
    }

    private TodoData data(List<TodoItem> items, LocalDateTime generatedAt) {
        int pending = (int) items.stream().filter(item -> "HR_REQUEST_APPROVAL".equals(item.type())
                || "ARCHIVE_ACCESS_APPROVAL".equals(item.type())).count();
        int overdue = (int) items.stream().filter(item -> "ARCHIVE_RETURN_DUE".equals(item.type())
                && item.dueAt() != null && item.dueAt().isBefore(generatedAt)).count();
        int dueSoon = (int) items.stream().filter(item -> "ARCHIVE_RETURN_DUE".equals(item.type())
                && item.dueAt() != null && !item.dueAt().isBefore(generatedAt)
                && !item.dueAt().isAfter(generatedAt.plusDays(3))).count();
        int ocrFailed = (int) items.stream().filter(item -> "OCR_FAILED".equals(item.type())).count();
        return new TodoData(List.copyOf(items), items.size(), pending, dueSoon, overdue, ocrFailed, generatedAt);
    }

    private int priority(String value) {
        return switch (value) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    /** 按距到期日的天数给合同提醒定优先级：≤1 天 HIGH，≤7 天 MEDIUM，其余 LOW。 */
    private String reminderPriority(LocalDateTime dueAt, LocalDateTime now) {
        if (dueAt == null) {
            return "MEDIUM";
        }
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), dueAt.toLocalDate());
        if (days <= 1) {
            return "HIGH";
        }
        if (days <= 7) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
