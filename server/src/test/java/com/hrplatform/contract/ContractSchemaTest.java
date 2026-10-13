package com.hrplatform.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContractSchemaTest {
    @Test
    void migrationStoresContractDatesRenewalAndArchiveVersionLink() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V7__contract_schema.sql")) {
            assertThat(input).as("contract migration must be packaged").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("employee_contract", "employee_id", "signed_on",
                    "starts_on", "ends_on", "renewal_status", "source_version_id");
        }
    }

    @Test
    void migrationCreatesContractReminderEventTableWithIdempotentStageKey() throws IOException {
        try (var input = getClass().getResourceAsStream("/db/migration/V21__contract_reminder_event.sql")) {
            assertThat(input).as("V21 reminder migration must be packaged").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).contains("contract_reminder_event", "contract_id", "due_date",
                    "reminder_stage", "uk_contract_stage", "PENDING", "ACKED");
        }
    }
}
