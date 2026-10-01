package com.hrplatform.inventory;

import org.junit.jupiter.api.Test;

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
}
