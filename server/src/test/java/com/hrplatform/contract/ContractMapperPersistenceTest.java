package com.hrplatform.contract;

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
class ContractMapperPersistenceTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContractMapper contractMapper;

    @BeforeEach
    void createTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee_contract");
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee");
        jdbcTemplate.execute("CREATE TABLE employee (id BIGINT PRIMARY KEY, department_id BIGINT)");
        jdbcTemplate.execute("""
                CREATE TABLE employee_contract (
                    id BIGINT PRIMARY KEY,
                    contract_no VARCHAR(64) NOT NULL,
                    employee_id BIGINT NOT NULL,
                    contract_type VARCHAR(32) NOT NULL,
                    signed_on DATE NOT NULL,
                    starts_on DATE NOT NULL,
                    ends_on DATE NOT NULL,
                    renewal_status VARCHAR(24) NOT NULL,
                    source_version_id BIGINT,
                    renewed_from_contract_id BIGINT,
                    notes VARCHAR(512),
                    version_no INT NOT NULL,
                    created_by BIGINT NOT NULL,
                    updated_by BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.update("INSERT INTO employee(id, department_id) VALUES (7, 10)");
        insertContract(1L, "CTR-FUTURE", LocalDate.now().plusDays(30));
        insertContract(2L, "CTR-EXPIRED", LocalDate.now().minusDays(1));
        insertContract(3L, "CTR-TOO-FAR", LocalDate.now().plusDays(90));
    }

    @AfterEach
    void dropTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee_contract");
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee");
    }

    @Test
    void dueFilterExcludesAlreadyExpiredContracts() {
        LocalDate today = LocalDate.now();
        var page = contractMapper.list(null, null, "DUE", null, null, "ALL", today,
                today.plusDays(60), 0, 20);
        long count = contractMapper.count(null, null, "DUE", null, null, "ALL", today,
                today.plusDays(60));

        assertThat(page).extracting(ContractRecord::contractNo).containsExactly("CTR-FUTURE");
        assertThat(count).isEqualTo(1L);
    }

    private void insertContract(Long id, String number, LocalDate endsOn) {
        jdbcTemplate.update("""
                INSERT INTO employee_contract
                    (id, contract_no, employee_id, contract_type, signed_on, starts_on, ends_on,
                     renewal_status, version_no, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 7, 'FIXED_TERM', ?, ?, ?, 'NOT_DUE', 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, number, endsOn.minusYears(1), endsOn.minusYears(1), endsOn);
    }
}
