INSERT INTO sys_permission (code, name, status)
VALUES
    ('ARCHIVE_READ', '查看档案', 'ACTIVE'),
    ('ARCHIVE_WRITE', '维护档案', 'ACTIVE'),
    ('ARCHIVE_ACCESS_CREATE', '发起档案访问申请', 'ACTIVE'),
    ('ARCHIVE_ACCESS_APPROVE', '审批档案访问申请', 'ACTIVE'),
    ('ARCHIVE_ACCESS_CHECKOUT', '领取档案', 'ACTIVE'),
    ('ARCHIVE_ACCESS_RETURN', '归还档案', 'ACTIVE'),
    ('INVENTORY_READ', '查看盘点', 'ACTIVE'),
    ('INVENTORY_WRITE', '执行盘点和处理异常', 'ACTIVE'),
    ('STATISTICS_READ', '查看统计', 'ACTIVE'),
    ('HR_REQUEST_READ', '查看人事申请', 'ACTIVE'),
    ('HR_REQUEST_CREATE', '发起人事申请', 'ACTIVE'),
    ('HR_REQUEST_APPROVE', '审批人事申请', 'ACTIVE')
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'ARCHIVE_READ', 'ARCHIVE_WRITE', 'ARCHIVE_ACCESS_CREATE', 'ARCHIVE_ACCESS_APPROVE',
    'ARCHIVE_ACCESS_CHECKOUT', 'ARCHIVE_ACCESS_RETURN', 'INVENTORY_READ', 'INVENTORY_WRITE',
    'STATISTICS_READ', 'HR_REQUEST_READ', 'HR_REQUEST_CREATE', 'HR_REQUEST_APPROVE'
)
WHERE r.code = 'SYSTEM_ADMIN';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'ARCHIVE_READ', 'ARCHIVE_WRITE', 'ARCHIVE_ACCESS_CREATE', 'ARCHIVE_ACCESS_APPROVE',
    'ARCHIVE_ACCESS_CHECKOUT', 'ARCHIVE_ACCESS_RETURN', 'INVENTORY_READ', 'INVENTORY_WRITE',
    'STATISTICS_READ', 'HR_REQUEST_READ', 'HR_REQUEST_CREATE', 'HR_REQUEST_APPROVE'
)
WHERE r.code = 'HR_ADMIN';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'ARCHIVE_READ', 'ARCHIVE_ACCESS_CREATE', 'ARCHIVE_ACCESS_APPROVE',
    'ARCHIVE_ACCESS_CHECKOUT', 'ARCHIVE_ACCESS_RETURN', 'INVENTORY_READ',
    'STATISTICS_READ', 'HR_REQUEST_READ', 'HR_REQUEST_CREATE', 'HR_REQUEST_APPROVE'
)
WHERE r.code = 'DEPT_MANAGER';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'ARCHIVE_READ', 'ARCHIVE_ACCESS_CREATE', 'HR_REQUEST_READ', 'HR_REQUEST_CREATE'
)
WHERE r.code = 'EMPLOYEE';
