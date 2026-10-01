package com.hrplatform.workflow.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.cache.CacheKeys;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.workflow.security.WorkflowActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TodoService {
    private static final Logger log = LoggerFactory.getLogger(TodoService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final int MAX_LIMIT = 100;
    private static final String OCR_FAILURE_TITLE = "OCR 识别失败";
    private final TodoMapper mapper;
    private final ArchiveTodoClient archiveClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public TodoService(TodoMapper mapper, ArchiveTodoClient archiveClient,
                       StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.archiveClient = archiveClient;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public TodoDtos.TodoData list(WorkflowActor actor, int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        int boundedLimit = Math.min(limit, MAX_LIMIT);
        String key = CacheKeys.todo(actor.userId(), actor.scope().cacheKey());
        TodoDtos.TodoData cached = readCache(key);
        if (cached != null) return limitCached(cached, boundedLimit);

        LocalDateTime now = LocalDateTime.now();
        TodoDtos.TodoData complete = load(actor, MAX_LIMIT, now);
        writeCache(key, complete);
        return limitCached(complete, boundedLimit);
    }

    private TodoDtos.TodoData limitCached(TodoDtos.TodoData cached, int limit) {
        List<TodoDtos.TodoItem> items = cached.items() == null ? List.of() : cached.items();
        List<TodoDtos.TodoItem> returnedItems = items.size() > limit
                ? new ArrayList<>(items.subList(0, limit)) : items;
        LocalDateTime generatedAt = cached.generatedAt() == null ? LocalDateTime.now() : cached.generatedAt();
        return data(returnedItems, generatedAt);
    }

    private TodoDtos.TodoData load(WorkflowActor actor, int limit, LocalDateTime now) {
        DataScope scope = actor.scope();
        if (scope.type() == DataScope.Type.NONE) return data(List.of(), now);
        String type = scope.type().name();
        List<TodoDtos.TodoItem> items = new ArrayList<>();
        mapper.findPendingHrRequests(type, scope.employeeId(), scope.departmentId(), limit)
                .forEach(row -> items.add(map(row, "HR_REQUEST_APPROVAL", "HIGH")));
        mapper.findPendingArchiveAccess(type, scope.employeeId(), scope.departmentId(), limit)
                .forEach(row -> items.add(map(row, "ARCHIVE_ACCESS_APPROVAL", "HIGH")));
        LocalDateTime dueSoonUntil = now.plusDays(3);
        mapper.findDueUses(type, scope.employeeId(), scope.departmentId(), now, dueSoonUntil, limit)
                .forEach(row -> items.add(map(row, "ARCHIVE_RETURN_DUE", row.dueAt().isBefore(now) ? "HIGH" : "MEDIUM")));
        archiveClient.listFailedOcr(actor.bearerToken(), limit).forEach(failure ->
                items.add(new TodoDtos.TodoItem(failure.bindingId(), "OCR_FAILED", OCR_FAILURE_TITLE,
                        failure.employeeId(), "FAILED", "MEDIUM", null, failure.updatedAt(), target(failure.employeeId()))));

        items.sort(Comparator.comparingInt((TodoDtos.TodoItem item) -> priority(item.priority()))
                .thenComparing(TodoDtos.TodoItem::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoDtos.TodoItem::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        List<TodoDtos.TodoItem> returnedItems = items.size() > limit
                ? new ArrayList<>(items.subList(0, limit)) : items;
        return data(returnedItems, now);
    }

    private TodoDtos.TodoData data(List<TodoDtos.TodoItem> items, LocalDateTime generatedAt) {
        int pending = (int) items.stream().filter(item -> item.type().equals("HR_REQUEST_APPROVAL")
                || item.type().equals("ARCHIVE_ACCESS_APPROVAL")).count();
        int overdue = (int) items.stream().filter(item -> item.type().equals("ARCHIVE_RETURN_DUE")
                && item.dueAt() != null && item.dueAt().isBefore(generatedAt)).count();
        int dueSoon = (int) items.stream().filter(item -> item.type().equals("ARCHIVE_RETURN_DUE")
                && item.dueAt() != null && !item.dueAt().isBefore(generatedAt)
                && !item.dueAt().isAfter(generatedAt.plusDays(3))).count();
        int ocr = (int) items.stream().filter(item -> item.type().equals("OCR_FAILED")).count();
        return new TodoDtos.TodoData(List.copyOf(items), items.size(), pending, dueSoon, overdue, ocr, generatedAt);
    }

    private TodoDtos.TodoItem map(TodoMapper.TodoRow row, String type, String priority) {
        return new TodoDtos.TodoItem(row.id(), type, row.title(), row.employeeId(), row.status(), priority,
                row.dueAt(), row.createdAt(), target(row.employeeId()));
    }

    private TodoDtos.TodoData readCache(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, TodoDtos.TodoData.class);
        } catch (RuntimeException | IOException exception) {
            log.warn("Todo cache read failed; using live data", exception);
            return null;
        }
    }

    private void writeCache(String key, TodoDtos.TodoData data) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(data), CACHE_TTL);
        } catch (RuntimeException | IOException exception) {
            log.warn("Todo cache write failed; continuing without cache", exception);
        }
    }

    private int priority(String value) {
        return switch (value) { case "HIGH" -> 0; case "MEDIUM" -> 1; default -> 2; };
    }

    private String target(Long employeeId) { return "/app/employees/" + employeeId; }
}
