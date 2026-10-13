package com.hrplatform.inventory;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InventoryCreateRequest(
        @NotEmpty(message = "versionIds 不能为空")
        @Size(max = 200, message = "单个盘点任务最多包含 200 个档案版本")
        List<@NotNull(message = "档案版本 ID 不能为空") @Positive(message = "档案版本 ID 必须为正数") Long> versionIds) {
}
