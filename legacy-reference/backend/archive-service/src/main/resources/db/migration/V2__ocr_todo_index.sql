ALTER TABLE ocr_binding
    ADD COLUMN error_message VARCHAR(1024) NULL AFTER status,
    ADD KEY idx_ocr_binding_status_updated_at (status, updated_at, id);
