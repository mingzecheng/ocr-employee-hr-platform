CREATE TABLE inventory_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_value VARCHAR(128) NULL,
    initiator_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_task_no (task_no),
    KEY idx_inventory_task_status_time (status, created_at),
    KEY idx_inventory_task_scope (scope_type, scope_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    archive_document_id BIGINT UNSIGNED NOT NULL,
    expected_version_id BIGINT UNSIGNED NOT NULL,
    actual_version_id BIGINT UNSIGNED NULL,
    actual_status VARCHAR(16) NULL,
    difference_type VARCHAR(32) NULL,
    difference_description VARCHAR(1024) NULL,
    checked_by BIGINT UNSIGNED NULL,
    checked_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_item_document (task_id, archive_document_id),
    KEY idx_inventory_item_difference (task_id, difference_type),
    CONSTRAINT fk_inventory_item_task FOREIGN KEY (task_id) REFERENCES inventory_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_resolution (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    operator_id BIGINT UNSIGNED NOT NULL,
    resolution VARCHAR(1024) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_inventory_resolution_task_time (task_id, created_at),
    CONSTRAINT fk_inventory_resolution_task FOREIGN KEY (task_id) REFERENCES inventory_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
