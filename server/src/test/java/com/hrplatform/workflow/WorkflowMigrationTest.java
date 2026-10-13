package com.hrplatform.workflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowMigrationTest {
    @Test
    void approvalDepartmentBackfillUsesDestinationOnlyForOnboarding() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V9__workflow_approval_department.sql")) {
            assertThat(input).as("workflow migration must be packaged").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("CASE WHEN r.request_type = 'ONBOARDING'")
                    .contains("ELSE e.department_id END");
        }
    }

    @Test
    void migrationMarksExistingApprovalsAsAlreadyApplied() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V11__employee_change_effective_date.sql")) {
            assertThat(input).as("effective-date migration must be packaged").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("WHERE status = 'APPROVED'")
                    .contains("employee_change_applied_at = COALESCE(completed_at, created_at, CURRENT_TIMESTAMP)");
        }
    }

    @Test
    void inventoryMigrationBackfillsResolutionStateAndOpenExceptions() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V12__inventory_exception_closure.sql")) {
            assertThat(input).as("inventory exception migration must be packaged").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("resolution_status", "exception_record", "NOT EXISTS")
                    .contains("'NOT_REQUIRED'", "'OPEN'", "idx_exception_inventory_status");
        }
    }

    @Test
    void archiveAccessDatesUseDatetimeBeyondTheTimestamp2038Limit() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V13__archive_access_datetime_range.sql")) {
            assertThat(input).as("archive access date migration must be packaged").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("MODIFY COLUMN start_at DATETIME")
                    .contains("MODIFY COLUMN due_at DATETIME");
        }
    }

    @Test
    void archiveUseMigrationTracksReturnOperatorAndReceivingEmployee() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V14__archive_use_handover_tracking.sql")) {
            assertThat(input).as("handover migration must be packaged").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("returned_by_user_id BIGINT NULL")
                    .contains("handover_to_employee_id BIGINT NULL")
                    .contains("REFERENCES sys_user(id)")
                    .contains("REFERENCES employee(id)");
        }
    }

    @Test
    void productionMigrationDisablesSeededDemoAccountsAndOrganization() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V16__disable_demo_data_outside_demo_profile.sql")) {
            assertThat(input).as("production demo-data migration must be packaged").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("username LIKE 'demo-%'", "employee_no LIKE 'DEMO-EMP-%'",
                    "code LIKE 'DEMO-%'", "LOWER('${demoAccountsEnabled}') <> 'true'");
        }
    }

    @Test
    void archiveAccessDecisionMigrationStoresTheActorAndDecisionReason() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V18__archive_access_decision_history.sql")) {
            assertThat(input).as("archive access decision migration must be packaged").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("archive_access_decision", "decided_by_user_id", "decision_note",
                    "REFERENCES archive_access_application(id)", "REFERENCES sys_user(id)", "'APPROVED', 'REJECTED'");
        }
    }

    @Test
    void leaveEffectiveWindowMigrationAddsNullableSwitchDates() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V19__leave_effective_dates.sql")) {
            assertThat(input).as("leave effective window migration must be packaged").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("ALTER TABLE leave_request_detail")
                    .contains("ADD COLUMN effective_from DATE NULL")
                    .contains("ADD COLUMN effective_to DATE NULL")
                    .contains("idx_leave_request_effective_window");
        }
    }
}
