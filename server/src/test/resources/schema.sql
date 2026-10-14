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

-- 角色/模块授权三表（与生产 V1 迁移一致；测试 profile 关闭 Flyway，故在此建表并种子角色）。
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(96) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

MERGE INTO sys_role (code, name) KEY(code) VALUES
    ('EMPLOYEE', '员工/业务经办人员'),
    ('DEPT_MANAGER', '部门负责人'),
    ('HR_ADMIN', '人事管理员'),
    ('SYSTEM_ADMIN', '系统管理员');
