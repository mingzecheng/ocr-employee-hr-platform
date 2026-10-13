package com.hrplatform;

import com.hrplatform.archive.ObjectStorage;
import com.hrplatform.access.AccessMapper;
import com.hrplatform.access.ArchiveAccessApplication;
import com.hrplatform.employee.EmployeeMapper;
import com.hrplatform.statistics.StatisticsMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "hr.archive-access.expiry.enabled=false",
        "hr.workflow.employee-change-cron=-"
})
@EnabledIfEnvironmentVariable(named = "HR_MYSQL_MIGRATION_TEST", matches = "true")
class FlywayMySqlMigrationTest {
    @Autowired
    private Flyway flyway;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AccessMapper accessMapper;
    @Autowired
    private EmployeeMapper employeeMapper;
    @Autowired
    private StatisticsMapper statisticsMapper;
    @MockitoBean
    private ObjectStorage objectStorage;
    private Fixture fixture;

    @AfterEach
    void removePersistenceFixtures() {
        if (fixture == null) {
            return;
        }
        if (fixture.applicationId != null) {
            jdbcTemplate.update("DELETE FROM archive_use_record WHERE application_id = ?", fixture.applicationId);
            jdbcTemplate.update("DELETE FROM archive_access_item WHERE application_id = ?", fixture.applicationId);
            jdbcTemplate.update("DELETE FROM archive_access_application WHERE id = ?", fixture.applicationId);
        }
        if (fixture.historyId != null) {
            jdbcTemplate.update("DELETE FROM employee_status_history WHERE id = ?", fixture.historyId);
        }
        if (fixture.versionId != null) {
            jdbcTemplate.update("DELETE FROM archive_version WHERE id = ?", fixture.versionId);
        }
        if (fixture.documentId != null) {
            jdbcTemplate.update("DELETE FROM archive_document WHERE id = ?", fixture.documentId);
        }
        if (fixture.fileObjectId != null) {
            jdbcTemplate.update("DELETE FROM file_object WHERE id = ?", fixture.fileObjectId);
        }
        if (fixture.employeeId != null) {
            jdbcTemplate.update("DELETE FROM employee WHERE id = ?", fixture.employeeId);
        }
        if (fixture.applicantId != null && fixture.operatorId != null) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", fixture.applicantId, fixture.operatorId);
        } else if (fixture.applicantId != null) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", fixture.applicantId);
        } else if (fixture.operatorId != null) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", fixture.operatorId);
        }
        if (fixture.positionId != null) {
            jdbcTemplate.update("DELETE FROM org_position WHERE id = ?", fixture.positionId);
        }
        if (fixture.departmentId != null) {
            jdbcTemplate.update("DELETE FROM org_department WHERE id = ?", fixture.departmentId);
        }
        fixture = null;
    }

    @Test
    void appliesTheFullMigrationChainAndCanBeInvokedAgain() {
        assertThat(flyway.info().applied()).hasSizeGreaterThanOrEqualTo(18);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'hr_request'
                """, Integer.class)).isEqualTo(1);

        flyway.migrate();

        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void mysqlReadGrantQueryEnforcesReviewUseExpiryAndReturnStates() {
        Fixture ids = insertReadGrantFixture();
        LocalDateTime now = LocalDateTime.now().withNano(0);

        assertThat(accessMapper.countActiveReadGrants(ids.versionId, ids.operatorId, true, now)).isEqualTo(1);
        assertThat(accessMapper.countActiveReadGrants(ids.versionId, ids.operatorId, false, now)).isZero();

        jdbcTemplate.update("UPDATE archive_access_application SET status = 'APPROVED' WHERE id = ?", ids.applicationId);
        assertThat(accessMapper.countActiveReadGrants(ids.versionId, ids.operatorId, true, now)).isZero();

        jdbcTemplate.update("UPDATE archive_access_application SET status = 'REJECTED' WHERE id = ?", ids.applicationId);
        assertThat(accessMapper.countActiveReadGrants(ids.versionId, ids.operatorId, true, now)).isZero();
        jdbcTemplate.update("UPDATE archive_access_application SET status = 'EXPIRED' WHERE id = ?", ids.applicationId);
        assertThat(accessMapper.countActiveReadGrants(ids.versionId, ids.operatorId, true, now)).isZero();

        jdbcTemplate.update("""
                UPDATE archive_access_application
                SET status = 'IN_USE', due_at = ? WHERE id = ?
                """, now.plusHours(1), ids.applicationId);
        jdbcTemplate.update("""
                INSERT INTO archive_use_record (application_id, operator_id, used_at)
                VALUES (?, ?, ?)
                """, ids.applicationId, ids.operatorId, now.minusMinutes(1));
        assertThat(accessMapper.countActiveReadGrants(ids.versionId, ids.operatorId, false, now)).isEqualTo(1);
        assertThat(accessMapper.countActiveReadGrants(ids.versionId, ids.applicantId + 999, false, now)).isZero();

        jdbcTemplate.update("UPDATE archive_access_application SET due_at = ? WHERE id = ?",
                now.minusSeconds(1), ids.applicationId);
        assertThat(accessMapper.countActiveReadGrants(ids.versionId, ids.operatorId, false, now)).isZero();

        jdbcTemplate.update("UPDATE archive_access_application SET status = 'ABNORMAL', due_at = ? WHERE id = ?",
                now.plusHours(1), ids.applicationId);
        assertThat(accessMapper.countActiveReadGrants(ids.versionId, ids.operatorId, false, now)).isZero();

        jdbcTemplate.update("UPDATE archive_use_record SET returned_at = ? WHERE application_id = ?",
                now.minusSeconds(1), ids.applicationId);
        jdbcTemplate.update("UPDATE archive_access_application SET status = 'RETURNED' WHERE id = ?", ids.applicationId);
        assertThat(accessMapper.countActiveReadGrants(ids.versionId, ids.operatorId, false, now)).isZero();
    }

    @Test
    void mysqlOptimisticStateUpdateAllowsOnlyOneConcurrentWriter() throws Exception {
        Fixture ids = insertReadGrantFixture();
        var workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = workers.submit(() -> updateAtTheSameVersion(ids, "APPROVED", ready, start));
            Future<Integer> second = workers.submit(() -> updateAtTheSameVersion(ids, "REJECTED", ready, start));
            ready.await();
            start.countDown();

            assertThat(first.get() + second.get()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT version_no FROM archive_access_application WHERE id = ?", Integer.class,
                    ids.applicationId))
                    .isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM archive_access_application WHERE id = ?", String.class,
                    ids.applicationId))
                    .isIn("APPROVED", "REJECTED");
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void mysqlEmployeeHistoryQueryRespectsCurrentDepartmentScope() {
        Fixture ids = insertReadGrantFixture();
        long historyId = insertId("""
                INSERT INTO employee_status_history
                    (employee_id, before_status, after_status, before_department_id, after_department_id,
                     before_position_id, after_position_id, operator_id, created_at)
                VALUES (?, 'PENDING_ONBOARD', 'ACTIVE', ?, ?, ?, ?, ?, ?)
                """, ids.employeeId, ids.departmentId, ids.departmentId, ids.positionId, ids.positionId,
                ids.applicantId, LocalDateTime.now().withNano(0));
        ids.historyId = historyId;

        var rows = employeeMapper.listStatusHistory(ids.employeeId, null, ids.departmentId, "DEPARTMENT", 0, 20);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(historyId);
            assertThat(row.beforeStatus()).isEqualTo("PENDING_ONBOARD");
            assertThat(row.afterStatus()).isEqualTo("ACTIVE");
            assertThat(row.afterDepartmentName()).isEqualTo("MySQL 测试部");
            assertThat(row.operatorName()).isEqualTo("测试员工");
        });
    }

    @Test
    void mysqlStatisticsReportQueriesTrendDepartmentAndCycleAggregates() {
        Fixture ids = insertReadGrantFixture();

        var trend = statisticsMapper.monthlyTrend("DEPARTMENT", null, ids.departmentId,
                null, null);
        var departments = statisticsMapper.departments("DEPARTMENT", null, ids.departmentId,
                null, null);
        var cycle = statisticsMapper.workflowCycle("DEPARTMENT", null, ids.departmentId,
                null, null);

        assertThat(trend).isNotEmpty();
        assertThat(departments).singleElement().satisfies(row -> {
            assertThat(row.departmentId()).isEqualTo(ids.departmentId);
            assertThat(row.departmentName()).isEqualTo("MySQL 测试部");
            assertThat(row.employeeCount()).isEqualTo(1);
            assertThat(row.archiveCount()).isZero();
        });
        assertThat(cycle.completedCount()).isZero();
    }

    private int updateAtTheSameVersion(Fixture ids, String status, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        start.await();
        return accessMapper.updateState(new ArchiveAccessApplication(ids.applicationId, ids.applicationNo,
                ids.applicantId, ids.departmentId, "测试授权", "READ", LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1), status, 2, LocalDateTime.now()));
    }

    private Fixture insertReadGrantFixture() {
        String token = UUID.randomUUID().toString().replace("-", "");
        String shortToken = token.substring(0, 20);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        fixture = new Fixture();
        fixture.departmentId = insertId("INSERT INTO org_department (code, name) VALUES (?, ?)",
                "MT-" + shortToken, "MySQL 测试部");
        fixture.positionId = insertId("INSERT INTO org_position (department_id, code, name) VALUES (?, ?, ?)",
                fixture.departmentId, "P-" + shortToken, "MySQL 测试岗");
        fixture.employeeId = insertId("""
                INSERT INTO employee (employee_no, name, department_id, position_id, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, "M-" + shortToken, "测试员工", fixture.departmentId, fixture.positionId);
        fixture.applicantId = insertId("""
                INSERT INTO sys_user (username, password_hash, employee_id, department_id)
                VALUES (?, 'not-used', ?, ?)
                """, "mysql-applicant-" + token, fixture.employeeId, fixture.departmentId);
        fixture.operatorId = insertId("""
                INSERT INTO sys_user (username, password_hash) VALUES (?, 'not-used')
                """, "mysql-operator-" + token);
        fixture.fileObjectId = insertId("""
                INSERT INTO file_object (object_key, original_name, content_type, byte_size, sha256)
                VALUES (?, ?, 'image/png', 1,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')
                """, "mysql-test/" + token + ".png", token + ".png");
        fixture.documentId = insertId("""
                INSERT INTO archive_document (employee_id, document_type, title)
                VALUES (?, 'employee_profile', '测试档案')
                """, fixture.employeeId);
        fixture.versionId = insertId("""
                INSERT INTO archive_version
                    (document_id, version_no, file_object_id, original_name, content_type, byte_size,
                     sha256, status, is_current, created_by, change_reason)
                VALUES (?, 1, ?, ?, 'image/png', 1,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        'UPLOADED', TRUE, ?, 'mysql integration test')
                """, fixture.documentId, fixture.fileObjectId, token + ".png", fixture.applicantId);
        fixture.applicationNo = "ACC-MYSQL-" + token;
        fixture.applicationId = insertId("""
                INSERT INTO archive_access_application
                    (application_no, applicant_id, department_id, purpose, use_type, start_at,
                     due_at, status, version_no, created_at)
                VALUES (?, ?, ?, '测试授权', 'READ', ?, ?, 'PENDING_APPROVAL', 1, ?)
                """, fixture.applicationNo, fixture.applicantId, fixture.departmentId,
                now.minusMinutes(1), now.plusHours(1), now);
        insertId("""
                INSERT INTO archive_access_item
                    (application_id, archive_version_id, employee_id, department_id, status)
                VALUES (?, ?, ?, ?, 'REQUESTED')
                """, fixture.applicationId, fixture.versionId, fixture.employeeId, fixture.departmentId);
        return fixture;
    }

    private long insertId(String sql, Object... parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private static final class Fixture {
        private Long departmentId;
        private Long positionId;
        private Long employeeId;
        private Long applicantId;
        private Long operatorId;
        private Long fileObjectId;
        private Long documentId;
        private Long versionId;
        private Long applicationId;
        private Long historyId;
        private String applicationNo;
    }
}
