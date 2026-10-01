package com.hrplatform.workflow.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ArchiveAccessPersistenceTest {
    @Autowired
    private ArchiveAccessService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void persistsAccessApprovalCheckoutAndAbnormalReturnHistory() {
        ArchiveAccessDtos.AccessData draft = service.create(
                new ArchiveAccessDtos.CreateRequest(42L, "PAPER_BORROW", "入职核验",
                        LocalDateTime.now(), LocalDateTime.now().plusDays(2), true,
                        List.of(new ArchiveAccessDtos.ItemRequest(501L, 601L, "FULL"))), 7L);

        service.submit(draft.id(), 7L);
        service.approve(draft.id(), 8L, true, "部门同意");
        service.approve(draft.id(), 9L, true, "人事同意");
        ArchiveAccessDtos.UseData use = service.checkout(draft.id(), 7L);
        ArchiveAccessDtos.UseData abnormal = service.returnUse(use.id(), 7L,
                new ArchiveAccessDtos.ReturnRequest("NORMAL", null, "档案袋缺失", "封面破损"));

        assertThat(abnormal.status()).isEqualTo("ABNORMAL");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM archive_access_application WHERE id = ?", String.class, draft.id()))
                .isEqualTo("ABNORMAL");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM archive_access_approval WHERE application_id = ?", Integer.class, draft.id()))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM archive_use_record WHERE application_id = ?", Integer.class, draft.id()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE resource_type = 'ARCHIVE_ACCESS' AND resource_id = ?",
                Integer.class, draft.id().toString()))
                .isEqualTo(5);
    }
}
