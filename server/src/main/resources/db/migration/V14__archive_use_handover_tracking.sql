ALTER TABLE archive_use_record
    ADD COLUMN returned_by_user_id BIGINT NULL AFTER returned_at,
    ADD COLUMN handover_to_employee_id BIGINT NULL AFTER returned_by_user_id,
    ADD CONSTRAINT fk_access_use_returned_by
        FOREIGN KEY (returned_by_user_id) REFERENCES sys_user(id),
    ADD CONSTRAINT fk_access_use_handover_employee
        FOREIGN KEY (handover_to_employee_id) REFERENCES employee(id);

CREATE INDEX idx_access_use_handover_employee
    ON archive_use_record (handover_to_employee_id, returned_at);
