package com.hrplatform.workflow.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InventoryPersistenceTest {
    @Autowired
    private InventoryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAbnormalInventoryAndResolutionEvidence() {
        InventoryDtos.TaskData draft = service.create(
                new InventoryDtos.CreateRequest("EMPLOYEE", "42",
                        List.of(new InventoryDtos.ItemRequest(501L, 601L))), 7L);

        service.start(draft.id(), 7L);
        service.checkItem(draft.id(), draft.items().get(0).id(), 7L,
                new InventoryDtos.CheckRequest(null, "MISSING", "材料未找到"));
        InventoryDtos.TaskData abnormal = service.complete(draft.id(), 7L);
        InventoryDtos.TaskData resolved = service.resolve(draft.id(), 8L,
                new InventoryDtos.ResolveRequest("已补录并重新归档"));

        assertThat(abnormal.status()).isEqualTo("ABNORMAL");
        assertThat(resolved.status()).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT difference_type FROM inventory_item WHERE task_id = ?", String.class, draft.id()))
                .isEqualTo("MISSING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_resolution WHERE task_id = ?", Integer.class, draft.id()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE resource_type = 'INVENTORY_TASK' AND resource_id = ?",
                Integer.class, draft.id().toString()))
                .isEqualTo(5);
    }
}
