-- N-1 审计日志风控指纹字段
-- 历史日志这些新字段为 NULL，不回填。
ALTER TABLE operation_log
    ADD COLUMN client_ip VARCHAR(45) NULL,
    ADD COLUMN user_agent VARCHAR(512) NULL,
    ADD COLUMN request_method VARCHAR(10) NULL,
    ADD COLUMN request_uri VARCHAR(512) NULL,
    ADD COLUMN session_id VARCHAR(128) NULL;
