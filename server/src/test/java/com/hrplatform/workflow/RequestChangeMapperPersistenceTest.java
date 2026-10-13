package com.hrplatform.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RequestChangeMapperPersistenceTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RequestMapper requestMapper;

    @BeforeEach
    void createTables() {
        dropTables();
        jdbcTemplate.execute("CREATE TABLE hr_request (id BIGINT PRIMARY KEY, request_type VARCHAR(24), "
                + "status VARCHAR(32), employee_change_applied_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE onboarding_detail (request_id BIGINT PRIMARY KEY, hire_date DATE)");
        jdbcTemplate.execute("CREATE TABLE transfer_detail (request_id BIGINT PRIMARY KEY, effective_date DATE)");
        jdbcTemplate.execute("CREATE TABLE offboarding_detail (request_id BIGINT PRIMARY KEY, leave_date DATE)");

        jdbcTemplate.update("INSERT INTO hr_request VALUES (1, 'TRANSFER', 'APPROVED', NULL)");
        jdbcTemplate.update("INSERT INTO hr_request VALUES (2, 'TRANSFER', 'APPROVED', NULL)");
        jdbcTemplate.update("INSERT INTO hr_request VALUES (3, 'TRANSFER', 'APPROVED', CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO hr_request VALUES (4, 'TRANSFER', 'REJECTED', NULL)");
        jdbcTemplate.update("INSERT INTO transfer_detail VALUES (1, ?)", LocalDate.now().minusDays(1));
        jdbcTemplate.update("INSERT INTO transfer_detail VALUES (2, ?)", LocalDate.now().plusDays(1));
        jdbcTemplate.update("INSERT INTO transfer_detail VALUES (3, ?)", LocalDate.now().minusDays(3));
        jdbcTemplate.update("INSERT INTO transfer_detail VALUES (4, ?)", LocalDate.now().minusDays(4));
    }

    @AfterEach
    void dropTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS offboarding_detail");
        jdbcTemplate.execute("DROP TABLE IF EXISTS transfer_detail");
        jdbcTemplate.execute("DROP TABLE IF EXISTS onboarding_detail");
        jdbcTemplate.execute("DROP TABLE IF EXISTS hr_request");
    }

    @Test
    void dueChangeQueryExcludesFutureAppliedAndRejectedRequests() {
        assertThat(requestMapper.listDueEmployeeChangeIds(LocalDate.now(), 100)).containsExactly(1L);
        assertThat(requestMapper.lockDueEmployeeChange(1L, LocalDate.now())).isEqualTo(1L);
        assertThat(requestMapper.lockDueEmployeeChange(2L, LocalDate.now())).isNull();
    }
}
