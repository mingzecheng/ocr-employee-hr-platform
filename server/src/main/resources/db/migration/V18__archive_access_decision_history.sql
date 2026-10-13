CREATE TABLE archive_access_decision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    decided_by_user_id BIGINT NOT NULL,
    decision VARCHAR(16) NOT NULL,
    decision_note VARCHAR(512) NULL,
    decided_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_access_decision_application (application_id, decided_at, id),
    CONSTRAINT fk_access_decision_application FOREIGN KEY (application_id)
        REFERENCES archive_access_application(id),
    CONSTRAINT fk_access_decision_user FOREIGN KEY (decided_by_user_id)
        REFERENCES sys_user(id),
    CONSTRAINT ck_access_decision_type CHECK (decision IN ('APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
