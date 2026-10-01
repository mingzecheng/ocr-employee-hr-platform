package com.hrplatform.inventory;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InventoryCreateRequest(@NotEmpty List<Long> versionIds) {
}
