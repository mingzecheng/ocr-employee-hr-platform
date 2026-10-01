package com.hrplatform.workflow.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {
    private final InventoryMapper mapper = mock(InventoryMapper.class);
    private final InventoryService service = new InventoryService(mapper, new ObjectMapper());

    @Test
    void createsDraftWithExpectedVersionScope() {
        when(mapper.insertTask(any(InventoryTask.class))).thenAnswer(invocation -> {
            InventoryTask task = invocation.getArgument(0);
            task.setId(101L);
            return 1;
        });

        InventoryDtos.TaskData result = service.create(createRequest(), 7L);

        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.initiatorId()).isEqualTo(7L);
        verify(mapper).insertItem(any(InventoryItem.class));
    }

    @Test
    void matchingChecksCompleteTaskWithoutDifference() {
        InventoryTask task = task("DRAFT");
        InventoryItem item = item(201L, 101L, 501L, 601L);
        when(mapper.findTaskById(101L)).thenReturn(task);
        when(mapper.findItems(101L)).thenReturn(List.of(item));
        when(mapper.findItemById(201L)).thenReturn(item);

        service.start(101L, 7L);
        InventoryDtos.ItemData checked = service.checkItem(101L, 201L, 7L,
                new InventoryDtos.CheckRequest(601L, "PRESENT", null));
        InventoryDtos.TaskData completed = service.complete(101L, 7L);

        assertThat(checked.actualStatus()).isEqualTo("PRESENT");
        assertThat(checked.differenceType()).isEqualTo("NONE");
        assertThat(completed.status()).isEqualTo("COMPLETED");
        verify(mapper).updateTaskState(eq(101L), eq("COMPLETED"), any(), any());
    }

    @Test
    void differenceMovesTaskToAbnormalAndResolutionIsRetained() {
        InventoryTask task = task("DRAFT");
        InventoryItem item = item(201L, 101L, 501L, 601L);
        when(mapper.findTaskById(101L)).thenReturn(task);
        when(mapper.findItems(101L)).thenReturn(List.of(item));
        when(mapper.findItemById(201L)).thenReturn(item);
        when(mapper.findResolutionByTaskId(101L)).thenReturn(List.of());

        service.start(101L, 7L);
        service.checkItem(101L, 201L, 7L,
                new InventoryDtos.CheckRequest(null, "MISSING", "材料未找到"));
        InventoryDtos.TaskData abnormal = service.complete(101L, 7L);
        InventoryDtos.TaskData resolved = service.resolve(101L, 8L,
                new InventoryDtos.ResolveRequest("已补录材料并重新归档"));

        assertThat(abnormal.status()).isEqualTo("ABNORMAL");
        assertThat(resolved.status()).isEqualTo("COMPLETED");
        verify(mapper).insertResolution(any(InventoryResolution.class));
        verify(mapper).updateTaskState(eq(101L), eq("COMPLETED"), any(), any());
    }

    @Test
    void refusesCompletionWithUncheckedItemsAndRepeatedCheck() {
        InventoryTask task = task("DRAFT");
        InventoryItem item = item(201L, 101L, 501L, 601L);
        when(mapper.findTaskById(101L)).thenReturn(task);
        when(mapper.findItems(101L)).thenReturn(List.of(item));
        when(mapper.findItemById(201L)).thenReturn(item);

        service.start(101L, 7L);
        assertThatThrownBy(() -> service.complete(101L, 7L))
                .isInstanceOf(InventoryStateException.class)
                .hasMessageContaining("unchecked");

        service.checkItem(101L, 201L, 7L,
                new InventoryDtos.CheckRequest(601L, "PRESENT", null));
        assertThatThrownBy(() -> service.checkItem(101L, 201L, 7L,
                new InventoryDtos.CheckRequest(601L, "PRESENT", null)))
                .isInstanceOf(InventoryStateException.class)
                .hasMessageContaining("already checked");
    }

    private InventoryDtos.CreateRequest createRequest() {
        return new InventoryDtos.CreateRequest("EMPLOYEE", "42",
                List.of(new InventoryDtos.ItemRequest(501L, 601L)));
    }

    private InventoryTask task(String status) {
        InventoryTask task = new InventoryTask();
        task.setId(101L);
        task.setTaskNo("INV-TEST");
        task.setScopeType("EMPLOYEE");
        task.setScopeValue("42");
        task.setInitiatorId(7L);
        task.setStatus(status);
        return task;
    }

    private InventoryItem item(long id, long taskId, long documentId, long versionId) {
        InventoryItem item = new InventoryItem();
        item.setId(id);
        item.setTaskId(taskId);
        item.setArchiveDocumentId(documentId);
        item.setExpectedVersionId(versionId);
        return item;
    }
}
