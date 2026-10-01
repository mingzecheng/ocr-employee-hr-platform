package com.hrplatform.archive.statistics;

import com.hrplatform.common.security.DataScope;
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
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StatisticsPersistenceTest {
    @Autowired
    private StatisticsService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long employeeId;
    private final List<Long> scopedEmployeeIds = new ArrayList<>();

    @Test
    void aggregatesPublishedArchiveAndOcrRowsFromArchiveDatabase() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        employeeId = insert("INSERT INTO employee (employee_no, name, status) VALUES (?, ?, 'ACTIVE')",
                "STAT-" + suffix, "统计测试员工");
        Long archiveRecordId = insert("INSERT INTO archive_record (employee_id, status) VALUES (?, 'ACTIVE')",
                employeeId);
        Long fileId = insert("INSERT INTO file_object (object_key, original_name, content_type, size_bytes, sha256, storage_provider) "
                        + "VALUES (?, 'stat.png', 'image/png', 1, REPEAT('a', 64), 'TEST')",
                "statistics/" + suffix);
        Long documentId = insert("INSERT INTO archive_document (archive_record_id, document_type, title, status) "
                        + "VALUES (?, 'PROFILE', '统计材料', 'ACTIVE')", archiveRecordId);
        Long versionId = insert("INSERT INTO archive_version (document_id, version_no, file_object_id, status) "
                        + "VALUES (?, 1, ?, 'PUBLISHED')", documentId, fileId);
        jdbcTemplate.update(
                "INSERT INTO ocr_binding (version_id, ocr_task_id, status, field_count) VALUES (?, ?, 'SUCCEEDED', 1)",
                versionId, "stat-task-" + suffix);

        StatisticsDtos.OverviewData result = service.overview(DataScope.all(0L));

        assertThat(result.activeEmployeeCount()).isGreaterThanOrEqualTo(1L);
        assertThat(result.archiveDocumentCount()).isGreaterThanOrEqualTo(1L);
        assertThat(result.ocrTaskCount()).isGreaterThanOrEqualTo(1L);
        assertThat(result.ocrSucceededCount()).isGreaterThanOrEqualTo(1L);
        assertThat(result.ocrSuccessRate()).isGreaterThan(0.0);
        assertThat(result.archiveCompletenessRate()).isGreaterThan(0.0);
    }

    @Test
    void departmentStatisticsExcludeEmployeesFromOtherDepartments() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Long inScope = insert("INSERT INTO employee (employee_no, name, department_id, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                "SCOPE-IN-" + suffix, "范围内员工", 9101L);
        Long outsideScope = insert("INSERT INTO employee (employee_no, name, department_id, status) "
                        + "VALUES (?, ?, ?, 'ACTIVE')",
                "SCOPE-OUT-" + suffix, "范围外员工", 9102L);
        scopedEmployeeIds.add(inScope);
        scopedEmployeeIds.add(outsideScope);

        StatisticsDtos.OverviewData result = service.overview(DataScope.department(9101L, 7L));

        assertThat(result.activeEmployeeCount()).isEqualTo(1L);
    }

    @AfterEach
    void cleanUp() {
        if (!scopedEmployeeIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM employee WHERE id IN (?, ?)",
                    scopedEmployeeIds.get(0), scopedEmployeeIds.get(1));
            scopedEmployeeIds.clear();
        }
        if (employeeId == null) {
            return;
        }
        jdbcTemplate.update("DELETE b FROM ocr_binding b JOIN archive_version v ON v.id = b.version_id "
                + "JOIN archive_document d ON d.id = v.document_id JOIN archive_record r ON r.id = d.archive_record_id "
                + "WHERE r.employee_id = ?", employeeId);
        jdbcTemplate.update("DELETE v FROM archive_version v JOIN archive_document d ON d.id = v.document_id "
                + "JOIN archive_record r ON r.id = d.archive_record_id WHERE r.employee_id = ?", employeeId);
        jdbcTemplate.update("DELETE d FROM archive_document d JOIN archive_record r ON r.id = d.archive_record_id "
                + "WHERE r.employee_id = ?", employeeId);
        jdbcTemplate.update("DELETE f FROM file_object f LEFT JOIN archive_version v ON v.file_object_id = f.id "
                + "WHERE v.id IS NULL AND f.object_key LIKE 'statistics/%'");
        jdbcTemplate.update("DELETE FROM archive_record WHERE employee_id = ?", employeeId);
        jdbcTemplate.update("DELETE FROM employee WHERE id = ?", employeeId);
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
}
