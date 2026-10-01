CREATE TABLE archive_access_application (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_no VARCHAR(64) NOT NULL,
    applicant_id BIGINT UNSIGNED NOT NULL,
    employee_id BIGINT UNSIGNED NOT NULL,
    use_type VARCHAR(32) NOT NULL,
    purpose VARCHAR(512) NOT NULL,
    start_at DATETIME(3) NOT NULL,
    due_at DATETIME(3) NOT NULL,
    return_required BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL,
    current_node VARCHAR(32) NOT NULL,
    submitted_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_access_application_no (application_no),
    KEY idx_access_applicant_status (applicant_id, status),
    KEY idx_access_expire_status (due_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE archive_access_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    archive_document_id BIGINT UNSIGNED NOT NULL,
    archive_version_id BIGINT UNSIGNED NULL,
    scope VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_access_item (application_id, archive_document_id, archive_version_id),
    KEY idx_access_item_version (archive_version_id),
    CONSTRAINT fk_access_item_application FOREIGN KEY (application_id)
        REFERENCES archive_access_application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE archive_access_approval (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    node_code VARCHAR(32) NOT NULL,
    approver_id BIGINT UNSIGNED NOT NULL,
    decision VARCHAR(16) NOT NULL,
    comment VARCHAR(512) NULL,
    decided_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_access_approval_application (application_id, node_code),
    KEY idx_access_approval_approver_time (approver_id, decided_at),
    CONSTRAINT fk_access_approval_application FOREIGN KEY (application_id)
        REFERENCES archive_access_application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE archive_use_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    receiver_id BIGINT UNSIGNED NOT NULL,
    checked_out_at DATETIME(3) NOT NULL,
    due_at DATETIME(3) NOT NULL,
    returned_at DATETIME(3) NULL,
    return_type VARCHAR(16) NULL,
    handover_to_id BIGINT UNSIGNED NULL,
    missing_description VARCHAR(1024) NULL,
    damage_description VARCHAR(1024) NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_use_application (application_id),
    KEY idx_use_status_due (status, due_at),
    CONSTRAINT fk_use_application FOREIGN KEY (application_id)
        REFERENCES archive_access_application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
