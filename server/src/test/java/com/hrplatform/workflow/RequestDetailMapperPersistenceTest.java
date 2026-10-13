package com.hrplatform.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RequestDetailMapperPersistenceTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RequestMapper requestMapper;

    @BeforeEach
    void createTables() {
        dropTables();
        jdbcTemplate.execute("CREATE TABLE org_department (id BIGINT PRIMARY KEY, name VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE org_position (id BIGINT PRIMARY KEY, name VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE employee (id BIGINT PRIMARY KEY, employee_no VARCHAR(32), name VARCHAR(128), department_id BIGINT, position_id BIGINT)");
        jdbcTemplate.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, username VARCHAR(64), employee_id BIGINT)");
        jdbcTemplate.execute("CREATE TABLE hr_request (id BIGINT PRIMARY KEY, employee_id BIGINT, applicant_id BIGINT, approval_department_id BIGINT, target_department_id BIGINT, target_position_id BIGINT)");
        jdbcTemplate.execute("CREATE TABLE approval_record (id BIGINT PRIMARY KEY, request_id BIGINT, node_code VARCHAR(24), approver_id BIGINT, decision VARCHAR(16), comment VARCHAR(512), decided_at TIMESTAMP)");

        jdbcTemplate.update("INSERT INTO org_department VALUES (10, '产品研发部'), (11, '数据平台部')");
        jdbcTemplate.update("INSERT INTO org_position VALUES (20, '后端工程师')");
        jdbcTemplate.update("INSERT INTO employee VALUES (7, 'EMP-007', '林晓', 10, 20)");
        jdbcTemplate.update("INSERT INTO employee VALUES (8, 'EMP-008', '周宁', 10, 20)");
        jdbcTemplate.update("INSERT INTO sys_user VALUES (31, 'EMP-008', 8)");
        jdbcTemplate.update("INSERT INTO hr_request VALUES (101, 7, 31, 10, 11, 20)");
        jdbcTemplate.update("INSERT INTO approval_record VALUES (41, 101, 'DEPARTMENT', 31, 'APPROVED', '同意', CURRENT_TIMESTAMP)");
    }

    @AfterEach
    void cleanUp() {
        dropTables();
    }

    @Test
    void detailContextResolvesEmployeeApplicantAndOrganizationNames() {
        RequestDisplayContext context = requestMapper.findDisplayContext(101L);

        assertThat(context.employeeNo()).isEqualTo("EMP-007");
        assertThat(context.employeeName()).isEqualTo("林晓");
        assertThat(context.employeeDepartmentId()).isEqualTo(10L);
        assertThat(context.employeeDepartmentName()).isEqualTo("产品研发部");
        assertThat(context.employeePositionId()).isEqualTo(20L);
        assertThat(context.employeePositionName()).isEqualTo("后端工程师");
        assertThat(context.applicantName()).isEqualTo("周宁");
        assertThat(context.approvalDepartmentName()).isEqualTo("产品研发部");
        assertThat(context.targetDepartmentName()).isEqualTo("数据平台部");
        assertThat(context.targetPositionName()).isEqualTo("后端工程师");
    }

    @Test
    void approvalHistoryResolvesApproverName() {
        assertThat(requestMapper.listApprovals(101L).get(0).approverName()).isEqualTo("周宁");
    }

    private void dropTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS approval_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS hr_request");
        jdbcTemplate.execute("DROP TABLE IF EXISTS sys_user");
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_position");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_department");
    }
}
