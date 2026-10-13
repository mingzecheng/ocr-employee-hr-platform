CREATE TABLE IF NOT EXISTS leave_request_detail (
    request_id BIGINT PRIMARY KEY,
    leave_type VARCHAR(24) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_leave_detail_request FOREIGN KEY (request_id) REFERENCES hr_request(id),
    CONSTRAINT ck_leave_date_order CHECK (end_date >= start_date),
    CONSTRAINT ck_leave_type CHECK (leave_type IN ('ANNUAL', 'SICK', 'PERSONAL', 'OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
