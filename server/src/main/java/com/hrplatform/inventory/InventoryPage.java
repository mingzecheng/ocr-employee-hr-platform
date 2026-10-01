package com.hrplatform.inventory;

import java.util.List;

public record InventoryPage(List<InventoryTask> items, long total, int page, int pageSize) {
}
