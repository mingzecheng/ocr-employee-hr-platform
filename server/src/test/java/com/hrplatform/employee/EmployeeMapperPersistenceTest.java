package com.hrplatform.employee;

import com.hrplatform.audit.OperationLogService;
import com.hrplatform.archive.ArchiveDocument;
import com.hrplatform.archive.ArchiveMapper;
import com.hrplatform.archive.ArchiveOcrSource;
import com.hrplatform.archive.ArchiveVersion;
import com.hrplatform.archive.FileObject;
import com.hrplatform.workflow.HrRequest;
import com.hrplatform.workflow.RequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeMapperPersistenceTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private ArchiveMapper archiveMapper;

    @Autowired
    private RequestMapper requestMapper;

    @BeforeEach
    void createEmployeeTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS employee");
        jdbcTemplate.execute("""
                CREATE TABLE employee (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    employee_no VARCHAR(32) NOT NULL UNIQUE,
                    name VARCHAR(128) NOT NULL,
                    phone VARCHAR(32),
                    department_id BIGINT NOT NULL,
                    position_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    hire_date DATE,
                    leave_date DATE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_version");
        jdbcTemplate.execute("DROP TABLE IF EXISTS file_object");
        jdbcTemplate.execute("DROP TABLE IF EXISTS archive_document");
        jdbcTemplate.execute("""
                CREATE TABLE archive_document (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    employee_id BIGINT NOT NULL,
                    document_type VARCHAR(64) NOT NULL,
                    title VARCHAR(128) NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(employee_id, document_type)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE file_object (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    object_key VARCHAR(512) NOT NULL UNIQUE,
                    original_name VARCHAR(255) NOT NULL,
                    content_type VARCHAR(128) NOT NULL,
                    byte_size BIGINT NOT NULL,
                    sha256 VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE archive_version (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    document_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    file_object_id BIGINT NOT NULL,
                    original_name VARCHAR(255) NOT NULL,
                    content_type VARCHAR(128) NOT NULL,
                    byte_size BIGINT NOT NULL,
                    sha256 VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    is_current BOOLEAN NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    created_by BIGINT,
                    change_reason VARCHAR(512),
                    UNIQUE(document_id, version_no)
                )
                """);
        jdbcTemplate.execute("DROP TABLE IF EXISTS leave_request_detail");
        jdbcTemplate.execute("DROP TABLE IF EXISTS offboarding_detail");
        jdbcTemplate.execute("DROP TABLE IF EXISTS transfer_detail");
        jdbcTemplate.execute("DROP TABLE IF EXISTS onboarding_detail");
        jdbcTemplate.execute("DROP TABLE IF EXISTS hr_request");
        jdbcTemplate.execute("""
                CREATE TABLE hr_request (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    request_no VARCHAR(64) NOT NULL UNIQUE,
                    request_type VARCHAR(24) NOT NULL,
                    employee_id BIGINT NOT NULL,
                    target_department_id BIGINT,
                    approval_department_id BIGINT NOT NULL,
                    target_position_id BIGINT,
                    applicant_id BIGINT NOT NULL,
                    source_version_id BIGINT,
                    payload_json CLOB,
                    status VARCHAR(32) NOT NULL,
                    current_node VARCHAR(24),
                    version_no INT NOT NULL,
                    submitted_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    employee_change_applied_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE leave_request_detail (
                    request_id BIGINT PRIMARY KEY,
                    leave_type VARCHAR(24),
                    start_date DATE,
                    end_date DATE,
                    reason VARCHAR(512)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE onboarding_detail (
                    request_id BIGINT PRIMARY KEY,
                    hire_date DATE,
                    contract_type VARCHAR(32)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE transfer_detail (
                    request_id BIGINT PRIMARY KEY,
                    from_department_id BIGINT,
                    from_position_id BIGINT,
                    effective_date DATE,
                    reason VARCHAR(512)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE offboarding_detail (
                    request_id BIGINT PRIMARY KEY,
                    leave_date DATE,
                    handover_person VARCHAR(128),
                    handover_status VARCHAR(32),
                    reason VARCHAR(512)
                )
                """);
    }

    @Test
    void employeeCreationPersistsAndReadsGeneratedId() {
        EmployeeService service = new EmployeeService(employeeMapper, mock(OperationLogService.class));

        Employee created = service.create(new EmployeeCreateRequest("示例员工", null,
                10L, 20L, EmployeeStatus.ACTIVE, null), 99L);

        assertThat(created.id()).isNotNull();
        assertThat(created.employeeNo()).matches("EMP-\\d{4}-[A-F0-9]{20}");
        assertThat(employeeMapper.findByEmployeeNo(created.employeeNo())).isEqualTo(created);
    }

    @Test
    void employeeProfileUpdatePersistsAndReadsBackBasicFieldsOnly() {
        EmployeeService service = new EmployeeService(employeeMapper, mock(OperationLogService.class));
        Employee created = service.create(new EmployeeCreateRequest("原姓名", "13800138001",
                10L, 20L, EmployeeStatus.ACTIVE, null), 99L);

        Employee updated = service.updateProfile(created.id(),
                new EmployeeProfileUpdateRequest("新姓名", null), 99L);

        assertThat(updated.name()).isEqualTo("新姓名");
        assertThat(updated.phone()).isNull();
        assertThat(updated.departmentId()).isEqualTo(created.departmentId());
        assertThat(updated.positionId()).isEqualTo(created.positionId());
        assertThat(updated.status()).isEqualTo(created.status());
    }

    @Test
    void archiveVersionRecordsPersistAndReadBackWithoutGeneratedKeyMutation() {
        ArchiveDocument draftDocument = new ArchiveDocument(null, 7L, "employee_profile", "员工档案");
        assertThat(archiveMapper.insertDocument(draftDocument)).isEqualTo(1);
        ArchiveDocument document = archiveMapper.findDocumentForUpdate(7L, "employee_profile");
        assertThat(document.id()).isNotNull();

        FileObject draftFile = new FileObject(null, "archive/7/profile.png", "profile.png",
                "image/png", 10L, "abc123");
        assertThat(archiveMapper.insertFile(draftFile)).isEqualTo(1);
        FileObject file = archiveMapper.findFileByObjectKey(draftFile.objectKey());
        assertThat(file.id()).isNotNull();

        ArchiveVersion draftVersion = new ArchiveVersion(null, document.id(), 1, file.id(), "profile.png",
                "image/png", 10L, "abc123", "UPLOADED", true, java.time.LocalDateTime.now(),
                99L, "initial upload");
        assertThat(archiveMapper.insertVersion(draftVersion)).isEqualTo(1);
        ArchiveVersion version = archiveMapper.findVersionByDocumentAndNumber(document.id(), 1);

        assertThat(version.id()).isNotNull();
        assertThat(version.fileObjectId()).isEqualTo(file.id());
        assertThat(version.isCurrent()).isTrue();
    }

    @Test
    void ocrSourceQueryMapsObjectReferenceFromArchiveVersion() {
        jdbcTemplate.update("""
                INSERT INTO employee(employee_no, name, department_id, position_id, status)
                VALUES ('EMP-OCR-301', '模拟员工', 10, 20, 'ACTIVE')
                """);
        Long employeeId = jdbcTemplate.queryForObject(
                "SELECT id FROM employee WHERE employee_no = 'EMP-OCR-301'", Long.class);
        ArchiveDocument document = new ArchiveDocument(null, employeeId, "employee_profile", "员工档案");
        archiveMapper.insertDocument(document);
        ArchiveDocument savedDocument = archiveMapper.findDocumentForUpdate(employeeId, "employee_profile");
        FileObject file = new FileObject(null, "archive/301/profile.png", "profile.png",
                "image/png", 10L, "abc123");
        archiveMapper.insertFile(file);
        FileObject savedFile = archiveMapper.findFileByObjectKey(file.objectKey());
        ArchiveVersion version = new ArchiveVersion(null, savedDocument.id(), 1, savedFile.id(), "profile.png",
                "image/png", 10L, "abc123", "UPLOADED", true, java.time.LocalDateTime.now(),
                99L, "initial upload");
        archiveMapper.insertVersion(version);
        ArchiveVersion savedVersion = archiveMapper.findVersionByDocumentAndNumber(savedDocument.id(), 1);

        ArchiveOcrSource source = archiveMapper.findOcrSourceByVersionIdWithScope(
                savedVersion.id(), null, null, "ALL");

        assertThat(source.objectKey()).isEqualTo("archive/301/profile.png");
        assertThat(source.originalName()).isEqualTo("profile.png");
        assertThat(source.contentType()).isEqualTo("image/png");
        assertThat(source.sha256()).isEqualTo("abc123");
        assertThat(source.createdBy()).isEqualTo(99L);
    }

    @Test
    void departmentWorkflowQueryUsesApprovalDepartmentSnapshot() {
        jdbcTemplate.update("""
                INSERT INTO employee(employee_no, name, department_id, position_id, status)
                VALUES ('EMP-201', '调动员工', 10, 2, 'ACTIVE')
                """);
        Long employeeId = jdbcTemplate.queryForObject(
                "SELECT id FROM employee WHERE employee_no = 'EMP-201'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO hr_request(request_no, request_type, employee_id, target_department_id,
                    approval_department_id, target_position_id, applicant_id, payload_json, status,
                    version_no, created_at)
                VALUES ('HR-201', 'ONBOARDING', ?, 20, 20, 3, 30, '{}',
                    'PENDING_DEPT_APPROVAL', 1, CURRENT_TIMESTAMP)
                """, employeeId);

        var items = requestMapper.list(null, null, null, 20L, "DEPARTMENT", 0, 20);
        long total = requestMapper.count(null, null, null, 20L, "DEPARTMENT");

        assertThat(total).isEqualTo(1L);
        assertThat(items).extracting(HrRequest::requestNo).containsExactly("HR-201");
    }
}
