CREATE TABLE IF NOT EXISTS archive_access_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_no VARCHAR(64) NOT NULL UNIQUE,
    applicant_id BIGINT NOT NULL,
    department_id BIGINT NULL,
    purpose VARCHAR(512) NOT NULL,
    use_type VARCHAR(32) NOT NULL,
    start_at TIMESTAMP NOT NULL,
    due_at TIMESTAMP NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    version_no INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_access_status_due (status, due_at),
    INDEX idx_access_applicant (applicant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS archive_access_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    archive_version_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',
    UNIQUE KEY uk_access_application_version (application_id, archive_version_id),
    CONSTRAINT fk_access_item_application FOREIGN KEY (application_id) REFERENCES archive_access_application(id),
    CONSTRAINT fk_access_item_version FOREIGN KEY (archive_version_id) REFERENCES archive_version(id),
    CONSTRAINT fk_access_item_employee FOREIGN KEY (employee_id) REFERENCES employee(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS archive_use_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    used_at TIMESTAMP NOT NULL,
    returned_at TIMESTAMP NULL,
    return_status VARCHAR(24) NULL,
    remark VARCHAR(512) NULL,
    INDEX idx_access_use_active (application_id, returned_at),
    CONSTRAINT fk_access_use_application FOREIGN KEY (application_id) REFERENCES archive_access_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    initiator_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_inventory_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    archive_version_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    actual_version_id BIGINT NULL,
    actual_status VARCHAR(24) NULL,
    difference_type VARCHAR(32) NULL,
    difference_description VARCHAR(512) NULL,
    checked_by BIGINT NULL,
    checked_at TIMESTAMP NULL,
    CONSTRAINT fk_inventory_item_task FOREIGN KEY (task_id) REFERENCES inventory_task(id),
    CONSTRAINT fk_inventory_item_version FOREIGN KEY (archive_version_id) REFERENCES archive_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exception_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    inventory_item_id BIGINT NOT NULL,
    exception_type VARCHAR(32) NOT NULL,
    description VARCHAR(512) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    resolved_by BIGINT NULL,
    resolved_at TIMESTAMP NULL,
    resolution VARCHAR(512) NULL,
    CONSTRAINT fk_exception_inventory_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
