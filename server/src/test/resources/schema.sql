CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT,
    action VARCHAR(64) NOT NULL,
    object_type VARCHAR(64) NOT NULL,
    object_id BIGINT,
    result VARCHAR(32) NOT NULL,
    before_summary CLOB,
    after_summary CLOB,
    trace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_ip VARCHAR(45),
    user_agent VARCHAR(512),
    request_method VARCHAR(10),
    request_uri VARCHAR(512),
    session_id VARCHAR(128)
);
