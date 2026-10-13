package com.hrplatform.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InventoryCheckRequest(@NotBlank @Size(max = 24) String actualStatus,
                                    Long actualVersionId,
                                    @Size(max = 512) String note) {
}
