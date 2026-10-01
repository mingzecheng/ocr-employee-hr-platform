CREATE TABLE IF NOT EXISTS hr_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL UNIQUE,
    request_type VARCHAR(24) NOT NULL,
    employee_id BIGINT NOT NULL,
    target_department_id BIGINT NULL,
    target_position_id BIGINT NULL,
    applicant_id BIGINT NOT NULL,
    source_version_id BIGINT NULL,
    payload_json JSON NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_node VARCHAR(24) NULL,
    version_no INT NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_hr_request_status_type (status, request_type, created_at),
    INDEX idx_hr_request_employee_status (employee_id, status),
    CONSTRAINT fk_hr_request_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_hr_request_department FOREIGN KEY (target_department_id) REFERENCES org_department(id),
    CONSTRAINT fk_hr_request_position FOREIGN KEY (target_position_id) REFERENCES org_position(id),
    CONSTRAINT fk_hr_request_version FOREIGN KEY (source_version_id) REFERENCES archive_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS onboarding_detail (
    request_id BIGINT PRIMARY KEY,
    hire_date DATE NULL,
    contract_type VARCHAR(32) NULL,
    CONSTRAINT fk_onboarding_request FOREIGN KEY (request_id) REFERENCES hr_request(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transfer_detail (
    request_id BIGINT PRIMARY KEY,
    from_department_id BIGINT NULL,
    from_position_id BIGINT NULL,
    effective_date DATE NULL,
    reason VARCHAR(512) NULL,
    CONSTRAINT fk_transfer_request FOREIGN KEY (request_id) REFERENCES hr_request(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS offboarding_detail (
    request_id BIGINT PRIMARY KEY,
    leave_date DATE NULL,
    handover_person VARCHAR(128) NULL,
    handover_status VARCHAR(32) NULL,
    reason VARCHAR(512) NULL,
    CONSTRAINT fk_offboarding_request FOREIGN KEY (request_id) REFERENCES hr_request(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS approval_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    node_code VARCHAR(24) NOT NULL,
    approver_id BIGINT NOT NULL,
    decision VARCHAR(16) NOT NULL,
    comment VARCHAR(512) NULL,
    decided_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_approval_request_node (request_id, node_code),
    CONSTRAINT fk_approval_request FOREIGN KEY (request_id) REFERENCES hr_request(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
