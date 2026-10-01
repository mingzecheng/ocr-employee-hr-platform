package com.hrplatform.archive.ocr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OcrFailurePersistenceTest {
    @Autowired
    private OcrBindingMapper bindingMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> employeeIds = new ArrayList<>();
    private String objectKeyPrefix;

    @Test
    void departmentScopeReturnsOnlyFailedBindingsFromThatDepartment() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        objectKeyPrefix = "todo-ocr/" + suffix + "/";
        long inScopeEmployee = createFailure("in-scope", 9301L, "in-scope failure").employeeId();
        createFailure("out-of-scope", 9302L, "out-of-scope failure");

        List<OcrFailureData> failures = bindingMapper.listFailuresInScope(
                100, "DEPARTMENT", null, 9301L);

        assertThat(failures).singleElement().satisfies(failure -> {
            assertThat(failure.employeeId()).isEqualTo(inScopeEmployee);
            assertThat(failure.taskId()).startsWith("ocr-task-");
            assertThat(failure.errorMessage()).isEqualTo("in-scope failure");
        });
    }

    @Test
    void scopesFilterInactiveAndNonFailedRowsAndOrderWithLimit() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        objectKeyPrefix = "todo-ocr/" + suffix + "/";
        FailureFixture older = createFailure("older", 9301L, "older failure");
        FailureFixture newerLowId = createFailure("newer-low-id", 9301L, "newer low id failure");
        FailureFixture newerHighId = createFailure("newer-high-id", 9301L, "newer high id failure");
        FailureFixture inactiveEmployee = createFixture("inactive-employee", 9301L, "INACTIVE", "ACTIVE",
                "ACTIVE", "FAILED", "inactive employee failure");
        FailureFixture inactiveRecord = createFixture("inactive-record", 9301L, "ACTIVE", "ARCHIVED",
                "ACTIVE", "FAILED", "inactive record failure");
        FailureFixture inactiveDocument = createFixture("inactive-document", 9301L, "ACTIVE", "ACTIVE",
                "ARCHIVED", "FAILED", "inactive document failure");
        FailureFixture nonFailed = createFixture("pending", 9301L, "ACTIVE", "ACTIVE", "ACTIVE", "PENDING",
                "pending binding");

        LocalDateTime oldest = LocalDateTime.of(2026, 9, 20, 9, 0);
        LocalDateTime newest = LocalDateTime.of(2026, 9, 20, 11, 0);
        setUpdatedAt(older.bindingId(), oldest);
        setUpdatedAt(newerLowId.bindingId(), newest);
        setUpdatedAt(newerHighId.bindingId(), newest);

        List<OcrFailureData> all = bindingMapper.listFailuresInScope(100, "ALL", null, null);
        assertThat(all).extracting(OcrFailureData::bindingId)
                .containsExactly(newerHighId.bindingId(), newerLowId.bindingId(), older.bindingId());
        assertThat(all).extracting(OcrFailureData::employeeId)
                .doesNotContain(inactiveEmployee.employeeId(), inactiveRecord.employeeId(), inactiveDocument.employeeId());
        assertThat(all).extracting(OcrFailureData::bindingId).doesNotContain(nonFailed.bindingId());

        List<OcrFailureData> limited = bindingMapper.listFailuresInScope(2, "ALL", null, null);
        assertThat(limited).extracting(OcrFailureData::bindingId)
                .containsExactly(newerHighId.bindingId(), newerLowId.bindingId());

        assertThat(bindingMapper.listFailuresInScope(100, "EMPLOYEE", newerLowId.employeeId(), null))
                .extracting(OcrFailureData::bindingId).containsExactly(newerLowId.bindingId());
        assertThat(bindingMapper.listFailuresInScope(100, "NONE", null, null)).isEmpty();
    }

    @AfterEach
    void cleanUp() {
        if (employeeIds.isEmpty()) {
            return;
        }
        String placeholders = employeeIds.stream().map(id -> "?").reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        jdbcTemplate.update("DELETE b FROM ocr_binding b JOIN archive_version v ON v.id = b.version_id "
                + "JOIN archive_document d ON d.id = v.document_id JOIN archive_record r ON r.id = d.archive_record_id "
                + "WHERE r.employee_id IN (" + placeholders + ")", employeeIds.toArray());
        jdbcTemplate.update("DELETE v FROM archive_version v JOIN archive_document d ON d.id = v.document_id "
                + "JOIN archive_record r ON r.id = d.archive_record_id WHERE r.employee_id IN (" + placeholders + ")",
                employeeIds.toArray());
        jdbcTemplate.update("DELETE d FROM archive_document d JOIN archive_record r ON r.id = d.archive_record_id "
                + "WHERE r.employee_id IN (" + placeholders + ")", employeeIds.toArray());
        jdbcTemplate.update("DELETE FROM file_object WHERE object_key LIKE ?", objectKeyPrefix + "%");
        jdbcTemplate.update("DELETE FROM archive_record WHERE employee_id IN (" + placeholders + ")",
                employeeIds.toArray());
        jdbcTemplate.update("DELETE FROM employee WHERE id IN (" + placeholders + ")", employeeIds.toArray());
        employeeIds.clear();
    }

    private FailureFixture createFailure(String label, long departmentId, String errorMessage) {
        return createFixture(label, departmentId, "ACTIVE", "ACTIVE", "ACTIVE", "FAILED", errorMessage);
    }

    private FailureFixture createFixture(String label, long departmentId, String employeeStatus, String recordStatus,
                                         String documentStatus, String bindingStatus, String errorMessage) {
        long employeeId = insert("INSERT INTO employee (employee_no, name, department_id, status) "
                        + "VALUES (?, ?, ?, ?)", "OCR-" + label + "-" + UUID.randomUUID(), label, departmentId,
                employeeStatus);
        employeeIds.add(employeeId);
        long recordId = insert("INSERT INTO archive_record (employee_id, status) VALUES (?, ?)", employeeId,
                recordStatus);
        long documentId = insert("INSERT INTO archive_document (archive_record_id, document_type, title, status) "
                + "VALUES (?, 'PROFILE', 'OCR test document', ?)", recordId, documentStatus);
        long fileId = insert("INSERT INTO file_object (object_key, original_name, content_type, size_bytes, sha256, storage_provider) "
                + "VALUES (?, 'ocr.png', 'image/png', 1, REPEAT('a', 64), 'TEST')",
                objectKeyPrefix + UUID.randomUUID());
        long versionId = insert("INSERT INTO archive_version (document_id, version_no, file_object_id, status) "
                + "VALUES (?, 1, ?, 'DRAFT')", documentId, fileId);
        long bindingId = insert("INSERT INTO ocr_binding (version_id, ocr_task_id, status, error_message, field_count) "
                        + "VALUES (?, ?, ?, ?, 0)",
                versionId, "ocr-task-" + UUID.randomUUID() + "-" + bindingStatus.toLowerCase(), bindingStatus,
                errorMessage);
        return new FailureFixture(employeeId, recordId, documentId, versionId, bindingId);
    }

    private void setUpdatedAt(long bindingId, LocalDateTime updatedAt) {
        jdbcTemplate.update("UPDATE ocr_binding SET updated_at = ? WHERE id = ?",
                Timestamp.valueOf(updatedAt), bindingId);
    }

    private long insert(String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private record FailureFixture(long employeeId, long recordId, long documentId, long versionId, long bindingId) {
    }
}
