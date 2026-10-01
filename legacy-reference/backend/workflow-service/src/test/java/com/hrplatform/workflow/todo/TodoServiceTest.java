package com.hrplatform.workflow.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.workflow.security.WorkflowActor;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TodoServiceTest {
    private final TodoMapper mapper = mock(TodoMapper.class);
    private final ArchiveTodoClient archiveClient = mock(ArchiveTodoClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final TodoService service = new TodoService(mapper, archiveClient, redis, objectMapper);

    TodoServiceTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void departmentScopeUsesJwtScopeAndForwardsToken() {
        when(values.get(any())).thenReturn(null);
        when(mapper.findPendingHrRequests("DEPARTMENT", null, 9L, 100))
                .thenReturn(List.of(row(1L, "HR_REQUEST_APPROVAL", 41L, LocalDateTime.now().plusDays(1), LocalDateTime.now())));
        when(archiveClient.listFailedOcr("department-token", 100)).thenReturn(List.of());

        TodoDtos.TodoData result = service.list(new WorkflowActor(7L, DataScope.department(9L, 7L), "department-token"), 50);

        assertThat(result.items()).extracting(TodoDtos.TodoItem::type)
                .containsExactly("HR_REQUEST_APPROVAL");
        verify(mapper).findPendingArchiveAccess("DEPARTMENT", null, 9L, 100);
        verify(archiveClient).listFailedOcr("department-token", 100);
    }

    @Test
    void allScopeUsesTokenAndSortsByPriorityThenDueDate() {
        when(values.get(any())).thenReturn(null);
        when(mapper.findPendingHrRequests("ALL", null, null, 100))
                .thenReturn(List.of(row(2L, "HR_REQUEST_APPROVAL", 42L, null, LocalDateTime.now())));
        when(mapper.findPendingArchiveAccess("ALL", null, null, 100))
                .thenReturn(List.of(row(3L, "ARCHIVE_ACCESS_APPROVAL", 43L, LocalDateTime.now().plusDays(2), LocalDateTime.now())));
        when(mapper.findDueUses(eq("ALL"), isNull(), isNull(), any(), any(), eq(100)))
                .thenReturn(List.of(row(4L, "ARCHIVE_RETURN_DUE_OVERDUE", 44L, LocalDateTime.now().minusDays(1), LocalDateTime.now())));
        when(archiveClient.listFailedOcr("all-token", 100)).thenReturn(List.of());

        TodoDtos.TodoData result = service.list(new WorkflowActor(7L, DataScope.all(7L), "all-token"), 50);

        assertThat(result.items()).extracting(TodoDtos.TodoItem::type)
                .containsExactly("ARCHIVE_RETURN_DUE", "ARCHIVE_ACCESS_APPROVAL", "HR_REQUEST_APPROVAL");
        assertThat(result.pendingApprovalCount()).isEqualTo(2);
        assertThat(result.overdueCount()).isEqualTo(1);
    }

    @Test
    void employeeScopeDoesNotAcceptAnyClientScope() {
        when(values.get(any())).thenReturn(null);
        when(archiveClient.listFailedOcr("employee-token", 100)).thenReturn(List.of());

        service.list(new WorkflowActor(7L, DataScope.employee(42L, 7L), "employee-token"), 150);

        verify(mapper).findPendingHrRequests("EMPLOYEE", 42L, null, 100);
        verify(mapper).findPendingArchiveAccess("EMPLOYEE", 42L, null, 100);
        verify(archiveClient).listFailedOcr("employee-token", 100);
    }

    @Test
    void cacheHitReturnsOnlyCachedTodoData() throws Exception {
        TodoDtos.TodoData cached = new TodoDtos.TodoData(List.of(item(1L, "HR_REQUEST_APPROVAL")), 1, 1, 0, 0, 0,
                LocalDateTime.of(2026, 9, 20, 10, 0));
        when(values.get(any())).thenReturn(objectMapper.writeValueAsString(cached));

        TodoDtos.TodoData result = service.list(new WorkflowActor(7L, DataScope.all(7L), "token"), 50);

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(mapper, archiveClient);
    }

    @Test
    void cacheHitAppliesRequestedLimitAndRecomputesCounts() throws Exception {
        TodoDtos.TodoData cached = new TodoDtos.TodoData(List.of(
                item(1L, "HR_REQUEST_APPROVAL"),
                item(2L, "OCR_FAILED")), 2, 1, 0, 0, 1,
                LocalDateTime.of(2026, 9, 20, 10, 0));
        when(values.get(any())).thenReturn(objectMapper.writeValueAsString(cached));

        TodoDtos.TodoData result = service.list(new WorkflowActor(7L, DataScope.all(7L), "token"), 1);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.pendingApprovalCount()).isEqualTo(1);
        assertThat(result.ocrFailedCount()).isZero();
        verifyNoInteractions(mapper, archiveClient);
    }

    @Test
    void cacheMissWritesCompleteDataForThirtySeconds() {
        when(values.get(any())).thenReturn(null);
        when(archiveClient.listFailedOcr("token", 100)).thenReturn(List.of());

        TodoDtos.TodoData result = service.list(new WorkflowActor(7L, DataScope.all(7L), "token"), 50);

        verify(values).set(any(), any(), eq(java.time.Duration.ofSeconds(30)));
        assertThat(result.total()).isZero();
    }

    @Test
    void cacheMissWarmsScopeCacheWithMaximumLimitSoLaterLargerRequestsDoNotLoseItems() {
        when(values.get(any())).thenReturn(null);
        when(mapper.findPendingHrRequests("ALL", null, null, 100))
                .thenReturn(List.of(row(1L, "HR_REQUEST_APPROVAL", 42L, null, LocalDateTime.now()),
                        row(2L, "HR_REQUEST_APPROVAL", 43L, null, LocalDateTime.now())));
        when(archiveClient.listFailedOcr("token", 100)).thenReturn(List.of());

        TodoDtos.TodoData result = service.list(new WorkflowActor(7L, DataScope.all(7L), "token"), 50);

        assertThat(result.total()).isEqualTo(2);
        verify(mapper).findPendingHrRequests("ALL", null, null, 100);
        verify(archiveClient).listFailedOcr("token", 100);
    }

    @Test
    void malformedCachedJsonFallsBackToLiveSources() {
        when(values.get(any())).thenReturn("{not-json");
        when(archiveClient.listFailedOcr("token", 100)).thenReturn(List.of());

        assertThat(service.list(new WorkflowActor(7L, DataScope.all(7L), "token"), 50).total()).isZero();
        verify(archiveClient).listFailedOcr("token", 100);
        verify(values).set(any(), any(), eq(java.time.Duration.ofSeconds(30)));
    }

    @Test
    void ocrFailureTodoDoesNotCacheDownstreamErrorDetails() throws Exception {
        when(values.get(any())).thenReturn(null);
        when(archiveClient.listFailedOcr("token", 100)).thenReturn(List.of(
                new TodoDtos.OcrFailureItem(9L, 10L, 42L, "task-1",
                        "failed at /srv/object-storage/private/key.png", LocalDateTime.now())));

        TodoDtos.TodoData result = service.list(new WorkflowActor(7L, DataScope.all(7L), "token"), 50);

        assertThat(result.items()).singleElement().extracting(TodoDtos.TodoItem::title)
                .isEqualTo("OCR 识别失败");
        org.mockito.ArgumentCaptor<String> cachedJson = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(any(), cachedJson.capture(), eq(java.time.Duration.ofSeconds(30)));
        assertThat(cachedJson.getValue()).doesNotContain("/srv/object-storage/private/key.png");
    }

    private TodoMapper.TodoRow row(long id, String sourceType, long employeeId,
                                   LocalDateTime dueAt, LocalDateTime createdAt) {
        return new TodoMapper.TodoRow(id, sourceType, "title", employeeId, "PENDING", dueAt, createdAt);
    }

    private TodoDtos.TodoItem item(long id, String type) {
        return new TodoDtos.TodoItem(id, type, "title", 42L, "PENDING", "HIGH", null,
                LocalDateTime.of(2026, 9, 20, 9, 0), "/app/employees/42");
    }
}
