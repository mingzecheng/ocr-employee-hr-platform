package com.hrplatform.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InventoryMapperPersistenceTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InventoryMapper inventoryMapper;

    @BeforeEach
    void createInventoryRelations() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS exception_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS inventory_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS inventory_task");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_version");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_document");
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee");
        jdbcTemplate.execute("CREATE TABLE employee (id BIGINT PRIMARY KEY, employee_no VARCHAR(32), name VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE archive_document (id BIGINT PRIMARY KEY, title VARCHAR(128))");
        jdbcTemplate.execute("CREATE TABLE archive_version (id BIGINT PRIMARY KEY, document_id BIGINT, version_no INT)");
        jdbcTemplate.execute("CREATE TABLE inventory_task (id BIGINT PRIMARY KEY)");
        jdbcTemplate.execute("""
                CREATE TABLE inventory_item (
                    id BIGINT PRIMARY KEY,
                    task_id BIGINT,
                    archive_version_id BIGINT,
                    employee_id BIGINT,
                    department_id BIGINT,
                    actual_version_id BIGINT,
                    actual_status VARCHAR(24),
                    difference_type VARCHAR(32),
                    difference_description VARCHAR(512),
                    checked_by BIGINT,
                    resolution_status VARCHAR(24)
                )
                """);
        jdbcTemplate.execute("CREATE TABLE exception_record (id BIGINT PRIMARY KEY, inventory_item_id BIGINT, resolution VARCHAR(512))");
        jdbcTemplate.update("INSERT INTO employee VALUES (7, 'EMP-007', '林晓')");
        jdbcTemplate.update("INSERT INTO archive_document VALUES (21, '劳动合同')");
        jdbcTemplate.update("INSERT INTO archive_version VALUES (45, 21, 1)");
        jdbcTemplate.update("INSERT INTO inventory_task VALUES (5)");
        jdbcTemplate.update("""
                INSERT INTO inventory_item (id, task_id, archive_version_id, employee_id, department_id,
                    resolution_status)
                VALUES (9, 5, 45, 7, 2, 'PENDING')
                """);
    }

    @Test
    void listItemsMapsBusinessLabelsFromEmployeeAndArchiveRelations() {
        var items = inventoryMapper.listItems(5L, null, null, "ALL");

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.employeeNo()).isEqualTo("EMP-007");
            assertThat(item.employeeName()).isEqualTo("林晓");
            assertThat(item.documentTitle()).isEqualTo("劳动合同");
        });
    }
}
