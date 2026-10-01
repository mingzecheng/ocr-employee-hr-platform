package com.hrplatform.workflow.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrplatform.common.security.DataScope;
import com.hrplatform.workflow.authorization.WorkflowAuthorizationClient;
import com.hrplatform.workflow.authorization.WorkflowDataScopeDeniedException;
import com.hrplatform.workflow.security.WorkflowActor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryDataScopeTest {
    private final InventoryMapper mapper = mock(InventoryMapper.class);
    private final WorkflowAuthorizationClient authorizationClient = mock(WorkflowAuthorizationClient.class);
    private final InventoryService service = new InventoryService(mapper, new ObjectMapper(), authorizationClient);
    private final WorkflowActor manager = new WorkflowActor(7L, DataScope.department(9L, 7L), "token");

    @Test
    void storesEmployeeAndDepartmentSnapshotForEveryInventoryItem() {
        when(authorizationClient.authorizeVersion(601L, "token"))
                .thenReturn(new WorkflowAuthorizationClient.AuthorizedResource(42L, 9L, 501L));
        when(mapper.insertTask(any(InventoryTask.class))).thenAnswer(invocation -> {
            InventoryTask task = invocation.getArgument(0);
            task.setId(101L);
            return 1;
        });

        service.create(request(501L, 601L), manager);

        var captor = org.mockito.ArgumentCaptor.forClass(InventoryItem.class);
        verify(mapper).insertItem(captor.capture());
        assertThat(captor.getValue().getEmployeeId()).isEqualTo(42L);
        assertThat(captor.getValue().getDepartmentId()).isEqualTo(9L);
    }

    @Test
    void rejectsInventoryItemFromAnotherArchiveDocument() {
        when(authorizationClient.authorizeVersion(601L, "token"))
                .thenReturn(new WorkflowAuthorizationClient.AuthorizedResource(42L, 9L, 999L));

        assertThatThrownBy(() -> service.create(request(501L, 601L), manager))
                .isInstanceOf(WorkflowDataScopeDeniedException.class);
    }

    private InventoryDtos.CreateRequest request(long documentId, long versionId) {
        return new InventoryDtos.CreateRequest("EMPLOYEE", "42",
                List.of(new InventoryDtos.ItemRequest(documentId, versionId)));
    }
}
