package com.hrplatform.inventory;

import com.hrplatform.common.security.DataScope;

public record InventoryActor(Long userId, DataScope scope) {
}
