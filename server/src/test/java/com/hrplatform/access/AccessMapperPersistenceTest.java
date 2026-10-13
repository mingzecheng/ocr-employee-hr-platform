package com.hrplatform.access;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AccessMapperPersistenceTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccessMapper accessMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void createTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_access_decision");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_access_resolution");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_version");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_document");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_access_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_use_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_access_application");
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sys_user");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_department");
        jdbcTemplate.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, username VARCHAR(64), employee_id BIGINT)");
        jdbcTemplate.execute("CREATE TABLE org_department (id BIGINT PRIMARY KEY, name VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE employee (id BIGINT PRIMARY KEY, employee_no VARCHAR(32), name VARCHAR(128), department_id BIGINT, position_id BIGINT, status VARCHAR(24))");
        jdbcTemplate.execute("CREATE TABLE archive_document (id BIGINT PRIMARY KEY, employee_id BIGINT, title VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE archive_version (id BIGINT PRIMARY KEY, document_id BIGINT, version_no INT)");
        jdbcTemplate.execute("CREATE TABLE archive_access_item (id BIGINT PRIMARY KEY, application_id BIGINT, archive_version_id BIGINT, employee_id BIGINT, department_id BIGINT, status VARCHAR(24))");
        jdbcTemplate.execute("""
                CREATE TABLE archive_access_application (
                    id BIGINT PRIMARY KEY,
                    application_no VARCHAR(64) NOT NULL,
                    applicant_id BIGINT NOT NULL,
                    department_id BIGINT,
                    purpose VARCHAR(512) NOT NULL,
                    use_type VARCHAR(32) NOT NULL,
                    start_at TIMESTAMP NOT NULL,
                    due_at TIMESTAMP NOT NULL,
                    status VARCHAR(24) NOT NULL,
                    version_no INT NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("CREATE TABLE archive_access_resolution (id BIGINT AUTO_INCREMENT PRIMARY KEY, application_id BIGINT NOT NULL, resolved_by_user_id BIGINT NOT NULL, resolution_note VARCHAR(512) NOT NULL, resolved_at TIMESTAMP NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE archive_access_decision (id BIGINT AUTO_INCREMENT PRIMARY KEY, application_id BIGINT NOT NULL, decided_by_user_id BIGINT NOT NULL, decision VARCHAR(16) NOT NULL, decision_note VARCHAR(512), decided_at TIMESTAMP NOT NULL)");
        jdbcTemplate.execute("""
                CREATE TABLE archive_use_record (
                    id BIGINT PRIMARY KEY,
                    application_id BIGINT NOT NULL,
                    operator_id BIGINT NOT NULL,
                    used_at TIMESTAMP NOT NULL,
                    returned_at TIMESTAMP,
                    return_status VARCHAR(24),
                    remark VARCHAR(512),
                    returned_by_user_id BIGINT,
                    handover_to_employee_id BIGINT
                )
                """);
        jdbcTemplate.update("INSERT INTO org_department VALUES (12, '产品研发部'), (13, '数据平台部')");
        jdbcTemplate.update("INSERT INTO sys_user(id, username, employee_id) VALUES (31, 'EMP-007', 7)");
        jdbcTemplate.update("INSERT INTO sys_user(id, username, employee_id) VALUES (32, 'EMP-008', 8)");
        jdbcTemplate.update("INSERT INTO employee(id, employee_no, name, department_id, position_id, status) VALUES (7, 'EMP-007', '林晓', 12, 4, 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO employee(id, employee_no, name, department_id, position_id, status) VALUES (8, 'EMP-008', '周宁', 12, 4, 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO employee(id, employee_no, name, department_id, position_id, status) VALUES (9, 'EMP-009', '王敏', 12, 4, 'INACTIVE')");
        jdbcTemplate.update("INSERT INTO employee(id, employee_no, name, department_id, position_id, status) VALUES (10, 'EMP-010', '陈安', 13, 5, 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO archive_document VALUES (5, 7, '劳动合同')");
        jdbcTemplate.update("INSERT INTO archive_version VALUES (70, 5, 3)");
        jdbcTemplate.update("""
                INSERT INTO archive_access_application
                    (id, application_no, applicant_id, department_id, purpose, use_type, start_at, due_at,
                     status, version_no, created_at)
                VALUES (90, 'ACC-90', 31, 12, '档案核验', 'READ', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        'PENDING', 1, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("INSERT INTO archive_access_item VALUES (60, 90, 70, 7, 12, 'REQUESTED')");
    }

    @AfterEach
    void dropTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_access_decision");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_access_resolution");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_version");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_document");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_access_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_use_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_access_application");
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sys_user");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_department");
    }

    @Test
    void abnormalResolutionHistoryHasPersistenceMappings() {
        var configuration = sqlSessionFactory.getConfiguration();

        assertThat(configuration.hasStatement("com.hrplatform.access.AccessMapper.insertResolution")).isTrue();
        assertThat(configuration.hasStatement("com.hrplatform.access.AccessMapper.listResolutions")).isTrue();
    }

    @Test
    void approvalDecisionHistoryPersistsDecisionReasonAndActor() {
        LocalDateTime decidedAt = LocalDateTime.now().minusMinutes(2);
        assertThat(accessMapper.insertDecision(new ArchiveAccessDecision(null, 90L, 31L, null,
                "REJECTED", "材料范围不符", decidedAt))).isEqualTo(1);

        ArchiveAccessDecision result = accessMapper.listDecisions(90L).get(0);

        assertThat(result.decision()).isEqualTo("REJECTED");
        assertThat(result.decisionNote()).isEqualTo("材料范围不符");
        assertThat(result.decidedByUserId()).isEqualTo(31L);
        assertThat(result.decidedByName()).isEqualTo("林晓");
        assertThat(result.decidedAt()).isEqualTo(decidedAt);
    }

    @Test
    void employeeScopeMapsApplicantAccountToDifferentEmployeeId() {
        var page = accessMapper.listApplications(31L, null, null, "EMPLOYEE", 7L, 0, 20);
        long count = accessMapper.countApplications(31L, null, null, "EMPLOYEE", 7L);

        assertThat(page).extracting(ArchiveAccessApplication::id).containsExactly(90L);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void readGrantRequiresPendingReviewOrAnActiveUnexpiredUse() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                UPDATE archive_access_application
                SET status = 'PENDING_APPROVAL', start_at = ?, due_at = ? WHERE id = 90
                """, now.minusHours(1), now.plusHours(1));

        assertThat(accessMapper.countActiveReadGrants(70L, 40L, true, now)).isEqualTo(1);
        assertThat(accessMapper.countActiveReadGrants(70L, 40L, false, now)).isZero();

        jdbcTemplate.update("UPDATE archive_access_application SET status = 'IN_USE' WHERE id = 90");
        jdbcTemplate.update("""
                INSERT INTO archive_use_record (id, application_id, operator_id, used_at)
                VALUES (50, 90, 31, ?)
                """, now.minusMinutes(5));

        assertThat(accessMapper.countActiveReadGrants(70L, 31L, false, now)).isEqualTo(1);
        assertThat(accessMapper.countActiveReadGrants(70L, 99L, false, now)).isZero();

        jdbcTemplate.update("UPDATE archive_use_record SET returned_at = ? WHERE id = 50", now);
        assertThat(accessMapper.countActiveReadGrants(70L, 31L, false, now)).isZero();

        jdbcTemplate.update("UPDATE archive_use_record SET returned_at = NULL WHERE id = 50");
        jdbcTemplate.update("UPDATE archive_access_application SET due_at = ? WHERE id = 90", now.minusSeconds(1));
        assertThat(accessMapper.countActiveReadGrants(70L, 31L, false, now)).isZero();
    }

    @Test
    void useRecordMapsReturnOperatorAndHandoverRecipient() {
        jdbcTemplate.update("""
                INSERT INTO archive_use_record
                    (id, application_id, operator_id, used_at, returned_at, return_status, remark,
                     returned_by_user_id, handover_to_employee_id)
                VALUES (41, 90, 31, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'HANDED_OVER', '交接完成', 32, 8)
                """);

        ArchiveUseRecord record = accessMapper.listUses(90L).get(0);
        ArchiveUseDetail detail = accessMapper.listUseDetails(90L).get(0);

        assertThat(record.returnStatus()).isEqualTo("HANDED_OVER");
        assertThat(record.returnedByUserId()).isEqualTo(32L);
        assertThat(record.handoverToEmployeeId()).isEqualTo(8L);
        assertThat(detail.operatorName()).isEqualTo("林晓");
        assertThat(detail.returnedByName()).isEqualTo("周宁");
        assertThat(detail.handoverToEmployeeName()).isEqualTo("周宁");
    }

    @Test
    void resolutionHistoryMapsNoteActorNameAndTimestamp() {
        LocalDateTime resolvedAt = LocalDateTime.now().minusMinutes(3);
        assertThat(accessMapper.insertResolution(new ArchiveAccessResolution(null, 90L, 31L, null,
                "已补齐缺页并复核", resolvedAt))).isEqualTo(1);

        ArchiveAccessResolution result = accessMapper.listResolutions(90L).get(0);

        assertThat(result.resolutionNote()).isEqualTo("已补齐缺页并复核");
        assertThat(result.resolvedByUserId()).isEqualTo(31L);
        assertThat(result.resolvedByName()).isEqualTo("林晓");
        assertThat(result.resolvedAt()).isEqualTo(resolvedAt);
    }

    @Test
    void detailQueriesResolveApplicantDepartmentArchiveTitleAndVersion() {
        ArchiveAccessDisplayContext context = accessMapper.findDisplayContext(90L);
        ArchiveAccessDetailItem item = accessMapper.listDetailItems(90L).get(0);

        assertThat(context.applicantName()).isEqualTo("林晓");
        assertThat(context.departmentName()).isEqualTo("产品研发部");
        assertThat(item.documentTitle()).isEqualTo("劳动合同");
        assertThat(item.versionNo()).isEqualTo(3);
        assertThat(item.employeeNo()).isEqualTo("EMP-007");
        assertThat(item.employeeName()).isEqualTo("林晓");
        assertThat(item.departmentName()).isEqualTo("产品研发部");
    }

    @Test
    void receiverMustBeActiveAndInApplicationDepartment() {
        assertThat(accessMapper.isActiveEmployeeInDepartment(8L, 12L)).isTrue();
        assertThat(accessMapper.isActiveEmployeeInDepartment(9L, 12L)).isFalse();
        assertThat(accessMapper.isActiveEmployeeInDepartment(10L, 12L)).isFalse();
    }

    @Test
    void overdueSweepMovesOnlyActiveExpiredApplicationsToAbnormal() {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(5);
        jdbcTemplate.update("""
                INSERT INTO archive_access_application
                    (id, application_no, applicant_id, department_id, purpose, use_type, start_at, due_at,
                     status, version_no, created_at)
                VALUES (91, 'ACC-91', 31, 12, '档案核验', 'READ', ?, ?, 'IN_USE', 4, ?)
                """, dueAt.minusHours(1), dueAt, dueAt.minusHours(1));
        jdbcTemplate.update("""
                INSERT INTO archive_use_record
                    (id, application_id, operator_id, used_at, returned_at, return_status, remark,
                     returned_by_user_id, handover_to_employee_id)
                VALUES (51, 91, 31, ?, NULL, NULL, NULL, NULL, NULL)
                """, dueAt.minusMinutes(30));

        assertThat(accessMapper.listOverdueInUseIds(LocalDateTime.now(), 100)).containsExactly(91L);
        assertThat(accessMapper.markOverdueInUse(91L, LocalDateTime.now())).isEqualTo(1);
        assertThat(accessMapper.listOverdueInUseIds(LocalDateTime.now(), 100)).isEmpty();
        assertThat(jdbcTemplate.queryForMap("SELECT status, version_no FROM archive_access_application WHERE id = 91"))
                .containsEntry("STATUS", "ABNORMAL")
                .containsEntry("VERSION_NO", 5);
        assertThat(accessMapper.markOverdueInUse(91L, LocalDateTime.now())).isZero();
    }

    @Test
    void unusedApprovedSweepExpiresOnlyApprovedApplicationsPastTheirDeadline() {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(5);
        jdbcTemplate.update("""
                INSERT INTO archive_access_application
                    (id, application_no, applicant_id, department_id, purpose, use_type, start_at, due_at,
                     status, version_no, created_at)
                VALUES (92, 'ACC-92', 31, 12, '档案核验', 'READ', ?, ?, 'APPROVED', 2, ?)
                """, dueAt.minusHours(1), dueAt, dueAt.minusHours(1));

        assertThat(accessMapper.listExpiredApprovedIds(LocalDateTime.now(), 100)).containsExactly(92L);
        assertThat(accessMapper.markApprovedExpired(92L, LocalDateTime.now())).isEqualTo(1);
        assertThat(accessMapper.listExpiredApprovedIds(LocalDateTime.now(), 100)).isEmpty();
        assertThat(jdbcTemplate.queryForMap("SELECT status, version_no FROM archive_access_application WHERE id = 92"))
                .containsEntry("STATUS", "EXPIRED")
                .containsEntry("VERSION_NO", 3);
        assertThat(accessMapper.markApprovedExpired(92L, LocalDateTime.now())).isZero();
    }
}
