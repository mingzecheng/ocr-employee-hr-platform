package com.hrplatform.workflow.todo;

import com.hrplatform.common.security.DataScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TodoPersistenceTest {
    @Autowired
    private TodoMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM archive_use_record");
        jdbcTemplate.update("DELETE FROM archive_access_approval");
        jdbcTemplate.update("DELETE FROM archive_access_item");
        jdbcTemplate.update("DELETE FROM archive_access_application");
        jdbcTemplate.update("DELETE FROM approval_record");
        jdbcTemplate.update("DELETE FROM hr_request");
    }

    @Test
    void filtersByDepartmentAndOrdersPendingRowsDeterministically() {
        jdbcTemplate.update("DELETE FROM archive_use_record");
        jdbcTemplate.update("DELETE FROM archive_access_approval");
        jdbcTemplate.update("DELETE FROM archive_access_item");
        jdbcTemplate.update("DELETE FROM archive_access_application");
        jdbcTemplate.update("DELETE FROM approval_record");
        jdbcTemplate.update("DELETE FROM hr_request");
        jdbcTemplate.update("INSERT INTO hr_request(request_no, request_type, employee_id, target_department_id, applicant_id, payload_json, status, current_node, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "TODO-DEPT-1", "ONBOARDING", 501L, 77L, 1L, "{}", "PENDING_HR_APPROVAL", "HR_APPROVAL", LocalDateTime.of(2026, 9, 20, 10, 0));
        jdbcTemplate.update("INSERT INTO hr_request(request_no, request_type, employee_id, target_department_id, applicant_id, payload_json, status, current_node, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "TODO-DEPT-2", "TRANSFER", 502L, 88L, 1L, "{}", "PENDING_DEPT_APPROVAL", "DEPT_APPROVAL", LocalDateTime.of(2026, 9, 20, 11, 0));
        jdbcTemplate.update("INSERT INTO hr_request(request_no, request_type, employee_id, target_department_id, applicant_id, payload_json, status, current_node, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "TODO-DEPT-3", "TRANSFER", 503L, 77L, 1L, "{}", "PENDING_DEPT_APPROVAL", "DEPT_APPROVAL", LocalDateTime.of(2026, 9, 20, 12, 0));

        assertThat(mapper.findPendingHrRequests("DEPARTMENT", null, 77L, 50))
                .extracting(TodoMapper.TodoRow::employeeId)
                .containsExactly(503L, 501L);
        assertThat(mapper.findPendingHrRequests("EMPLOYEE", 501L, null, 50))
                .extracting(TodoMapper.TodoRow::employeeId)
                .containsExactly(501L);
    }

    @Test
    void dueUsesSeparateOverdueAndDueSoonWindows() {
        assertThat(mapper.findDueUses("NONE", null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(3), 50))
                .isEmpty();
    }
}
