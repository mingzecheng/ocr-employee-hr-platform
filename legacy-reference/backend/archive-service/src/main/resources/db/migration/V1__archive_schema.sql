CREATE TABLE employee (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_no VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    id_card_no VARCHAR(64) NULL,
    department_id BIGINT UNSIGNED NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_no (employee_no),
    UNIQUE KEY uk_employee_id_card_no (id_card_no),
    KEY idx_employee_name (name),
    KEY idx_employee_department_status (department_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE archive_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employee_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_archive_record_employee (employee_id),
    CONSTRAINT fk_archive_record_employee FOREIGN KEY (employee_id) REFERENCES employee (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE archive_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    archive_record_id BIGINT UNSIGNED NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_archive_document_record_type (archive_record_id, document_type),
    CONSTRAINT fk_archive_document_record FOREIGN KEY (archive_record_id) REFERENCES archive_record (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE file_object (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(512) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    sha256 CHAR(64) NOT NULL,
    storage_provider VARCHAR(32) NOT NULL DEFAULT 'MINIO',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_object_key (object_key),
    KEY idx_file_object_sha256 (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE archive_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    file_object_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_archive_version_number (document_id, version_no),
    KEY idx_archive_version_file (file_object_id),
    CONSTRAINT fk_archive_version_document FOREIGN KEY (document_id) REFERENCES archive_document (id),
    CONSTRAINT fk_archive_version_file FOREIGN KEY (file_object_id) REFERENCES file_object (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ocr_binding (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    version_id BIGINT UNSIGNED NOT NULL,
    ocr_task_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    engine_version VARCHAR(255) NULL,
    detection_preview_url VARCHAR(512) NULL,
    detection_preview_content_type VARCHAR(128) NULL,
    detection_preview_size BIGINT UNSIGNED NULL,
    detection_preview_sha256 CHAR(64) NULL,
    detection_preview_width INT UNSIGNED NULL,
    detection_preview_height INT UNSIGNED NULL,
    field_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ocr_binding_version (version_id),
    UNIQUE KEY uk_ocr_binding_task (ocr_task_id),
    CONSTRAINT fk_ocr_binding_version FOREIGN KEY (version_id) REFERENCES archive_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ocr_field_confirmation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    binding_id BIGINT UNSIGNED NOT NULL,
    field_code VARCHAR(128) NOT NULL,
    original_value VARCHAR(512) NULL,
    confirmed_value VARCHAR(512) NOT NULL,
    confidence DECIMAL(8,6) NULL,
    operator_id BIGINT UNSIGNED NULL,
    reason VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ocr_confirmation_binding_field_created (binding_id, field_code, created_at),
    CONSTRAINT fk_ocr_confirmation_binding FOREIGN KEY (binding_id) REFERENCES ocr_binding (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NULL,
    action_code VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    trace_id VARCHAR(64) NULL,
    detail_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_archive_audit_resource_created (resource_type, resource_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
