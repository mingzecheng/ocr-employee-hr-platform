ALTER TABLE hr_request
    ADD COLUMN target_department_id BIGINT UNSIGNED NULL AFTER employee_id,
    ADD KEY idx_hr_request_target_department_status (target_department_id, status);

ALTER TABLE archive_access_application
    ADD COLUMN target_department_id BIGINT UNSIGNED NULL AFTER employee_id,
    ADD KEY idx_access_target_department_status (target_department_id, status);

ALTER TABLE inventory_item
    ADD COLUMN employee_id BIGINT UNSIGNED NULL AFTER archive_document_id,
    ADD COLUMN department_id BIGINT UNSIGNED NULL AFTER employee_id,
    ADD KEY idx_inventory_item_resource_scope (employee_id, department_id);
