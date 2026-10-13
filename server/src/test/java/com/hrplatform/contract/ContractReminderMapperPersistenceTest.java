package com.hrplatform.contract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ContractReminderMapperPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContractReminderMapper mapper;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void createTables() {
        dropTables();
        jdbcTemplate.execute("CREATE TABLE employee (id BIGINT PRIMARY KEY, name VARCHAR(128), department_id BIGINT)");
        jdbcTemplate.execute("""
                CREATE TABLE employee_contract (
                    id BIGINT PRIMARY KEY,
                    employee_id BIGINT NOT NULL,
                    ends_on DATE NOT NULL,
                    renewal_status VARCHAR(24) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE contract_reminder_event (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    contract_id BIGINT NOT NULL,
                    due_date DATE NOT NULL,
                    reminder_stage VARCHAR(10) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    acked_at TIMESTAMP NULL
                )
                """);

        jdbcTemplate.update("INSERT INTO employee (id, name, department_id) VALUES (7, '林晓', 10), (8, '周宁', 20)");
        // contract 1: NOT_DUE, due in 5 days (dept 10)
        insertContract(1L, 7L, today.plusDays(5), "NOT_DUE");
        // contract 2: NOT_DUE, due tomorrow (dept 20)
        insertContract(2L, 8L, today.plusDays(1), "NOT_DUE");
        // contract 3: already RENEWED, due in 10 days — 不参与生成，也不该出现在待办
        insertContract(3L, 7L, today.plusDays(10), "RENEWED");
        // contract 4: NOT_DUE but already expired (past) — 不在 [today, ...] 窗口内
        insertContract(4L, 7L, today.minusDays(1), "NOT_DUE");
    }

    @AfterEach
    void dropTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS contract_reminder_event");
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee_contract");
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee");
    }

    private void insertContract(Long id, Long employeeId, LocalDate endsOn, String renewalStatus) {
        jdbcTemplate.update("INSERT INTO employee_contract (id, employee_id, ends_on, renewal_status) VALUES (?, ?, ?, ?)",
                id, employeeId, endsOn, renewalStatus);
    }

    private void insertEvent(Long id, Long contractId, LocalDate dueDate, String stage, String status) {
        jdbcTemplate.update("""
                INSERT INTO contract_reminder_event (id, contract_id, due_date, reminder_stage, status, created_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, id, contractId, dueDate, stage, status);
    }

    @Test
    void findDueContractsOnlyReturnsNotDueWithinWindow() {
        List<ContractReminderMapper.DueContractRow> rows =
                mapper.findDueContractsWithin(today, today.plusDays(30));

        // 按到期日升序：合同 2(明天到期) 在 合同 1(5天后) 前；RENEWED 的 3 与过期的 4 被排除
        assertThat(rows).extracting(ContractReminderMapper.DueContractRow::contractId)
                .containsExactly(2L, 1L);
    }

    @Test
    void existsReflectsInsertAndInsertIsIdempotentGuard() {
        assertThat(mapper.exists(1L, "30D")).isFalse();

        assertThat(mapper.insert(1L, today.plusDays(5), "30D")).isEqualTo(1);

        assertThat(mapper.exists(1L, "30D")).isTrue();
        // 同合同同阶段再插应当被业务层用 exists 拦住（表上 uk 兜底）
        assertThat(mapper.exists(1L, "7D")).isFalse();
    }

    @Test
    void todoAggregationReturnsOnlyPendingNotDecidedScopedEvents() {
        // contract 1: PENDING 30D
        insertEvent(101L, 1L, today.plusDays(5), "30D", "PENDING");
        // contract 1: ACKED 7D — 已知悉，不出现
        insertEvent(102L, 1L, today.plusDays(5), "7D", "ACKED");
        // contract 2: PENDING 1D
        insertEvent(103L, 2L, today.plusDays(1), "1D", "PENDING");
        // contract 3: PENDING 但合同已 RENEWED — 不出现
        insertEvent(104L, 3L, today.plusDays(10), "30D", "PENDING");

        // HR(ALL)：只应看到 101 与 103；按到期日升序，103(明天到期) 排在 101(5天后) 前
        List<ContractReminderMapper.ContractReminderTodoRow> all =
                mapper.findPendingForTodo("ALL", null, null, 100);
        assertThat(all).extracting(ContractReminderMapper.ContractReminderTodoRow::id)
                .containsExactly(103L, 101L);

        // 部门负责人(dept 20)：只看到本部门员工周宁的事件 103
        List<ContractReminderMapper.ContractReminderTodoRow> dept =
                mapper.findPendingForTodo("DEPARTMENT", null, 20L, 100);
        assertThat(dept).extracting(ContractReminderMapper.ContractReminderTodoRow::id)
                .containsExactly(103L);

        // 普通员工 scope：一律看不到合同提醒
        List<ContractReminderMapper.ContractReminderTodoRow> emp =
                mapper.findPendingForTodo("EMPLOYEE", 7L, null, 100);
        assertThat(emp).isEmpty();

        // due_at 由 due_date 映射而来；101 是林晓 5 天后到期那条
        ContractReminderMapper.ContractReminderTodoRow row101 =
                all.stream().filter(r -> r.id() == 101L).findFirst().orElseThrow();
        assertThat(row101.dueAt().toLocalDate()).isEqualTo(today.plusDays(5));
        assertThat(row101.title()).contains("林晓");
        assertThat(row101.resourceId()).isEqualTo(101L);
    }

    @Test
    void ackTransitionsPendingToAckedAndIsIdempotent() {
        insertEvent(201L, 1L, today.plusDays(5), "7D", "PENDING");
        LocalDateTime ackedAt = LocalDateTime.now();

        assertThat(mapper.ack(201L, ackedAt)).isEqualTo(1);

        ContractReminderEvent event = mapper.findById(201L);
        assertThat(event.status()).isEqualTo("ACKED");
        assertThat(event.ackedAt()).isNotNull();

        // 已 ACKED 再 ack 返回 0（业务层据此做幂等）
        assertThat(mapper.ack(201L, LocalDateTime.now())).isZero();
    }
}
