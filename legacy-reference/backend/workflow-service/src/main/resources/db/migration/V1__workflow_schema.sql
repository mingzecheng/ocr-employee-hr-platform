CREATE TABLE hr_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL,
    request_type VARCHAR(32) NOT NULL,
    employee_id BIGINT UNSIGNED NOT NULL,
    applicant_id BIGINT UNSIGNED NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_node VARCHAR(32) NOT NULL,
    submitted_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_request_no (request_no),
    KEY idx_hr_request_employee_status (employee_id, request_type, status),
    KEY idx_hr_request_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE approval_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id BIGINT UNSIGNED NOT NULL,
    node_code VARCHAR(32) NOT NULL,
    approver_id BIGINT UNSIGNED NOT NULL,
    decision VARCHAR(16) NOT NULL,
    comment VARCHAR(512) NULL,
    decided_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_approval_request_node (request_id, node_code),
    KEY idx_approval_approver_time (approver_id, decided_at),
    CONSTRAINT fk_approval_request FOREIGN KEY (request_id) REFERENCES hr_request (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NULL,
    action_code VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    detail_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_workflow_audit_resource_time (resource_type, resource_id, created_at),
    KEY idx_workflow_audit_actor_time (actor_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
