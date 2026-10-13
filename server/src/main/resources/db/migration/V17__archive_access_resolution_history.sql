CREATE TABLE archive_access_resolution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    resolved_by_user_id BIGINT NOT NULL,
    resolution_note VARCHAR(512) NOT NULL,
    resolved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_access_resolution_application (application_id, resolved_at, id),
    CONSTRAINT fk_access_resolution_application FOREIGN KEY (application_id)
        REFERENCES archive_access_application(id),
    CONSTRAINT fk_access_resolution_user FOREIGN KEY (resolved_by_user_id)
        REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
