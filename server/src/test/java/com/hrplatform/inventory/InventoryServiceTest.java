package com.hrplatform.inventory;

import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceTest {
    @Test
    void completionIsBlockedWhenAnItemIsUnresolved() {
        InventoryTask task = new InventoryTask(1L, "INV-1", "IN_PROGRESS");
        InventoryItem item = new InventoryItem(2L, 1L, 8L, null, null, null);
        InventoryService service = new InventoryService(null);

        assertThatThrownBy(() -> service.complete(task, java.util.List.of(item)))
                .isInstanceOf(InventoryStateException.class);
    }

    @Test
    void listUsesDepartmentScope() {
        InventoryMapper mapper = Mockito.mock(InventoryMapper.class);
        Mockito.when(mapper.listTasks(8L, null, "DEPARTMENT", 0, 20))
                .thenReturn(java.util.List.of(new InventoryTask(1L, "INV-1", "DRAFT", 9L)));
        Mockito.when(mapper.countTasks(8L, null, "DEPARTMENT")).thenReturn(1L);

        InventoryPage page = new InventoryService(mapper).list(1, 20,
                new InventoryActor(9L, new DataScope(DataScope.Type.DEPARTMENT, 9L, null, 8L)));

        org.assertj.core.api.Assertions.assertThat(page.total()).isEqualTo(1L);
    }
}
