package com.hrplatform.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InventoryResolveRequest(@NotBlank @Size(max = 512) String resolution) {
}
