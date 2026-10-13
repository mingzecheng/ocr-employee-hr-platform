ALTER TABLE hr_request
    ADD COLUMN approval_department_id BIGINT NULL AFTER target_department_id;

UPDATE hr_request r
JOIN employee e ON e.id = r.employee_id
SET r.approval_department_id = CASE WHEN r.request_type = 'ONBOARDING'
                                    THEN COALESCE(r.target_department_id, e.department_id)
                                    ELSE e.department_id END
WHERE r.approval_department_id IS NULL;

ALTER TABLE hr_request
    MODIFY COLUMN approval_department_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_hr_request_approval_department
        FOREIGN KEY (approval_department_id) REFERENCES org_department(id),
    ADD INDEX idx_hr_request_approval_department (approval_department_id, status, created_at);
