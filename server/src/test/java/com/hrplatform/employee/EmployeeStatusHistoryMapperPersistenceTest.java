package com.hrplatform.employee;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeStatusHistoryMapperPersistenceTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmployeeMapper employeeMapper;

    @BeforeEach
    void createHistoryFixture() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee_status_history");
        jdbcTemplate.execute("DROP TABLE IF EXISTS hr_request");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sys_user");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_position");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_department");
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee");
        jdbcTemplate.execute("""
                CREATE TABLE org_department (
                    id BIGINT PRIMARY KEY, code VARCHAR(32), name VARCHAR(128)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE org_position (
                    id BIGINT PRIMARY KEY, department_id BIGINT, code VARCHAR(32), name VARCHAR(128)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE employee (
                    id BIGINT PRIMARY KEY, employee_no VARCHAR(32), name VARCHAR(128), phone VARCHAR(32),
                    department_id BIGINT, position_id BIGINT, status VARCHAR(24), hire_date DATE, leave_date DATE,
                    created_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, username VARCHAR(64), employee_id BIGINT)");
        jdbcTemplate.execute("""
                CREATE TABLE hr_request (
                    id BIGINT PRIMARY KEY, request_no VARCHAR(64), employee_id BIGINT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE employee_status_history (
                    id BIGINT PRIMARY KEY, employee_id BIGINT NOT NULL, before_status VARCHAR(24),
                    after_status VARCHAR(24) NOT NULL, before_department_id BIGINT, after_department_id BIGINT,
                    before_position_id BIGINT, after_position_id BIGINT, source_request_id BIGINT,
                    operator_id BIGINT, created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.update("INSERT INTO org_department VALUES (10, 'OLD', '原部门'), (20, 'NEW', '新部门')");
        jdbcTemplate.update("INSERT INTO org_position VALUES (11, 10, 'OLD-P', '原岗位'), (21, 20, 'NEW-P', '新岗位')");
        jdbcTemplate.update("""
                INSERT INTO employee
                    (id, employee_no, name, phone, department_id, position_id, status, created_at)
                VALUES (7, 'EMP-007', '林晓', NULL, 20, 21, 'ACTIVE', CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("INSERT INTO sys_user VALUES (99, 'hr-admin', NULL)");
        jdbcTemplate.update("INSERT INTO hr_request VALUES (501, 'HR-501', 7)");
        jdbcTemplate.update("""
                INSERT INTO employee_status_history
                    (id, employee_id, before_status, after_status, before_department_id, after_department_id,
                     before_position_id, after_position_id, source_request_id, operator_id, created_at)
                VALUES (601, 7, 'PENDING_ONBOARD', 'ACTIVE', 10, 20, 11, 21, 501, 99, ?)
                """, LocalDateTime.parse("2026-10-10T09:30:00"));
    }

    @Test
    void historyQueryMapsBeforeAfterLabelsSourceRequestAndOperator() {
        var rows = employeeMapper.listStatusHistory(7L, null, null, "ALL", 0, 20);
        long total = employeeMapper.countStatusHistory(7L, null, null, "ALL");

        assertThat(total).isEqualTo(1L);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(601L);
            assertThat(row.beforeStatus()).isEqualTo("PENDING_ONBOARD");
            assertThat(row.afterStatus()).isEqualTo("ACTIVE");
            assertThat(row.beforeDepartmentName()).isEqualTo("原部门");
            assertThat(row.afterDepartmentName()).isEqualTo("新部门");
            assertThat(row.beforePositionName()).isEqualTo("原岗位");
            assertThat(row.afterPositionName()).isEqualTo("新岗位");
            assertThat(row.requestNo()).isEqualTo("HR-501");
            assertThat(row.operatorName()).isEqualTo("hr-admin");
        });
    }

    @Test
    void departmentScopeCannotReadHistoryForAnotherCurrentDepartment() {
        assertThat(employeeMapper.listStatusHistory(7L, null, 10L, "DEPARTMENT", 0, 20)).isEmpty();
        assertThat(employeeMapper.countStatusHistory(7L, null, 10L, "DEPARTMENT")).isZero();
    }
}
