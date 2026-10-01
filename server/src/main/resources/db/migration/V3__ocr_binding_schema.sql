CREATE TABLE IF NOT EXISTS ocr_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    archive_version_id BIGINT NOT NULL,
    task_id VARCHAR(128) NULL,
    document_type VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    engine_version VARCHAR(128) NULL,
    processing_duration_ms BIGINT NOT NULL DEFAULT 0,
    review_required BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(512) NULL,
    preview_url VARCHAR(512) NULL,
    preview_content_type VARCHAR(128) NULL,
    preview_size BIGINT NULL,
    preview_sha256 CHAR(64) NULL,
    preview_width INT NULL,
    preview_height INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ocr_binding_version (archive_version_id),
    UNIQUE KEY uk_ocr_binding_task (task_id),
    INDEX idx_ocr_binding_status_time (status, updated_at),
    CONSTRAINT fk_ocr_binding_version FOREIGN KEY (archive_version_id) REFERENCES archive_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ocr_field_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    field_code VARCHAR(96) NOT NULL,
    previous_value VARCHAR(512) NULL,
    current_value VARCHAR(512) NOT NULL,
    reason VARCHAR(512) NULL,
    operator_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ocr_revision_binding_field_time (binding_id, field_code, created_at),
    CONSTRAINT fk_ocr_revision_binding FOREIGN KEY (binding_id) REFERENCES ocr_binding(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
