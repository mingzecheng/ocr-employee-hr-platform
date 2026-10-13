package com.hrplatform.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OperationLogMapperPersistenceTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OperationLogMapper mapper;

    @BeforeEach
    void createOperationLogTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS operation_log");
        jdbcTemplate.execute("""
                CREATE TABLE operation_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    operator_id BIGINT,
                    action VARCHAR(64) NOT NULL,
                    object_type VARCHAR(64) NOT NULL,
                    object_id BIGINT,
                    result VARCHAR(32) NOT NULL,
                    before_summary CLOB,
                    after_summary CLOB,
                    trace_id VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    client_ip VARCHAR(45),
                    user_agent VARCHAR(512),
                    request_method VARCHAR(10),
                    request_uri VARCHAR(512),
                    session_id VARCHAR(128)
                )
                """);
    }

    @Test
    void persistsSummariesAndFiltersInclusiveDateRangeAcrossPages() {
        insert(10L, LocalDateTime.parse("2026-10-10T23:59:59"), "status=DRAFT", "status=PENDING");
        insert(11L, LocalDateTime.parse("2026-10-11T00:00:00"), "status=PENDING", "status=APPROVED");
        insert(12L, LocalDateTime.parse("2026-10-11T23:59:59"), "status=IN_USE", "status=RETURNED");
        insert(13L, LocalDateTime.parse("2026-10-12T00:00:00"), "status=APPROVED", "status=EXPIRED");

        AuditPage page = new AuditController(mapper).list(null, "STATE_CHANGE", "HR_REQUEST", "SUCCESS",
                LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-11"), 2, 1).data();

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(record -> {
            assertThat(record.action()).isEqualTo("STATE_CHANGE");
            assertThat(record.createdAt()).isEqualTo(LocalDateTime.parse("2026-10-11T00:00:00"));
            assertThat(record.clientIpMasked()).isNull();
        });

        // 变更前后快照与风控指纹只在详情中可见。
        OperationLogRecord detail = mapper.findById(page.items().get(0).id());
        assertThat(detail.beforeSummary()).isEqualTo("status=PENDING");
        assertThat(detail.afterSummary()).isEqualTo("status=APPROVED");
    }

    private void insert(Long objectId, LocalDateTime createdAt, String beforeSummary, String afterSummary) {
        mapper.insert(new OperationLogRecord(null, 99L, "STATE_CHANGE", "HR_REQUEST", objectId,
                beforeSummary, afterSummary, "SUCCESS", "trace-audit-test", createdAt,
                null, null, null, null, null));
    }
}
