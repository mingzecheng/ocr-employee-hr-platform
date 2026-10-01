ALTER TABLE sys_user
    ADD COLUMN department_id BIGINT UNSIGNED NULL AFTER employee_id,
    ADD KEY idx_sys_user_department_status (department_id, status),
    ADD CONSTRAINT fk_sys_user_department
        FOREIGN KEY (department_id) REFERENCES org_department (id);
