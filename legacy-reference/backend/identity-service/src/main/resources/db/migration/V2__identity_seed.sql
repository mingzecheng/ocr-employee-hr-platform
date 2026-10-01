INSERT INTO sys_role (code, name, status)
VALUES
    ('SYSTEM_ADMIN', '系统管理员', 'ACTIVE'),
    ('HR_ADMIN', '人事管理员', 'ACTIVE'),
    ('DEPT_MANAGER', '部门负责人', 'ACTIVE'),
    ('EMPLOYEE', '普通员工', 'ACTIVE');

INSERT INTO sys_permission (code, name, status)
VALUES
    ('AUTH_LOGIN', '登录', 'ACTIVE'),
    ('AUTH_ME', '查看当前用户', 'ACTIVE'),
    ('ORG_READ', '查看组织', 'ACTIVE'),
    ('ORG_WRITE', '维护组织', 'ACTIVE'),
    ('AUDIT_READ', '查看审计', 'ACTIVE');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
WHERE r.code = 'SYSTEM_ADMIN';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN ('AUTH_LOGIN', 'AUTH_ME', 'ORG_READ', 'ORG_WRITE', 'AUDIT_READ')
WHERE r.code = 'HR_ADMIN';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN ('AUTH_LOGIN', 'AUTH_ME', 'ORG_READ')
WHERE r.code = 'DEPT_MANAGER';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN ('AUTH_LOGIN', 'AUTH_ME')
WHERE r.code = 'EMPLOYEE';

INSERT INTO org_department (parent_id, code, name, sort_no, status)
VALUES (NULL, 'HQ', '总部', 0, 'ACTIVE');
