ALTER TABLE hr_request
    ADD COLUMN employee_change_applied_at TIMESTAMP NULL AFTER completed_at;

UPDATE hr_request
SET employee_change_applied_at = COALESCE(completed_at, created_at, CURRENT_TIMESTAMP)
WHERE status = 'APPROVED';

CREATE INDEX idx_hr_request_due_employee_change
    ON hr_request (status, employee_change_applied_at, request_type);
