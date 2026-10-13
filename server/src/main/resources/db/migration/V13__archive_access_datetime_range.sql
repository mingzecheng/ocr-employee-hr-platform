ALTER TABLE archive_access_application
    MODIFY COLUMN start_at DATETIME NOT NULL,
    MODIFY COLUMN due_at DATETIME NOT NULL;
