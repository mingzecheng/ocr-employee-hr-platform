ALTER TABLE archive_access_application
    ADD CONSTRAINT fk_access_applicant
        FOREIGN KEY (applicant_id) REFERENCES sys_user(id);
