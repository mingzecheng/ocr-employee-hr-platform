-- Local, deterministic demonstration data. All records are prefixed with DEMO-.
INSERT INTO org_department (code, name, parent_id, sort_order, enabled)
VALUES ('DEMO-HR', '演示人事部', NULL, 10, TRUE),
       ('DEMO-OPS', '演示运营部', NULL, 20, TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name), enabled = VALUES(enabled);

SET @demo_hr_department = (SELECT id FROM org_department WHERE code = 'DEMO-HR' LIMIT 1);
SET @demo_ops_department = (SELECT id FROM org_department WHERE code = 'DEMO-OPS' LIMIT 1);

INSERT INTO org_position (department_id, code, name, enabled)
VALUES (@demo_hr_department, 'DEMO-HR-ADMIN', '演示人事专员', TRUE),
       (@demo_ops_department, 'DEMO-OPS-MANAGER', '演示运营负责人', TRUE),
       (@demo_ops_department, 'DEMO-OPS-STAFF', '演示运营专员', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name), enabled = VALUES(enabled);

SET @demo_hr_position = (SELECT id FROM org_position WHERE department_id = @demo_hr_department AND code = 'DEMO-HR-ADMIN' LIMIT 1);
SET @demo_ops_manager_position = (SELECT id FROM org_position WHERE department_id = @demo_ops_department AND code = 'DEMO-OPS-MANAGER' LIMIT 1);
SET @demo_ops_staff_position = (SELECT id FROM org_position WHERE department_id = @demo_ops_department AND code = 'DEMO-OPS-STAFF' LIMIT 1);

INSERT INTO employee (employee_no, name, phone, department_id, position_id, status, hire_date)
VALUES ('DEMO-EMP-001', '演示人事专员', '13800000001', @demo_hr_department, @demo_hr_position, 'ACTIVE', '2025-01-06'),
       ('DEMO-EMP-002', '演示运营负责人', '13800000002', @demo_ops_department, @demo_ops_manager_position, 'ACTIVE', '2025-02-10'),
       ('DEMO-EMP-003', '演示运营专员', '13800000003', @demo_ops_department, @demo_ops_staff_position, 'ACTIVE', '2025-03-03')
ON DUPLICATE KEY UPDATE name = VALUES(name), department_id = VALUES(department_id), position_id = VALUES(position_id), status = VALUES(status);

SET @demo_hr_employee = (SELECT id FROM employee WHERE employee_no = 'DEMO-EMP-001' LIMIT 1);
SET @demo_manager_employee = (SELECT id FROM employee WHERE employee_no = 'DEMO-EMP-002' LIMIT 1);
SET @demo_staff_employee = (SELECT id FROM employee WHERE employee_no = 'DEMO-EMP-003' LIMIT 1);

UPDATE org_department SET manager_employee_id = @demo_manager_employee WHERE id = @demo_ops_department;

INSERT INTO employee_archive (employee_id, completeness, confidentiality, status)
VALUES (@demo_hr_employee, 80, 'INTERNAL', 'ACTIVE'),
       (@demo_manager_employee, 100, 'INTERNAL', 'ACTIVE'),
       (@demo_staff_employee, 60, 'INTERNAL', 'ACTIVE')
ON DUPLICATE KEY UPDATE completeness = VALUES(completeness), status = VALUES(status);

-- The hash is a local demo-only BCrypt value for the documented password "password".
INSERT INTO sys_user (username, password_hash, employee_id, department_id, enabled)
VALUES ('demo-admin', '$2a$10$6es2.pcVQK85ODz0U/0FOe9iFoDogc8GzlmWt2K24Q3JkpXQ1y4/m', NULL, NULL, TRUE),
       ('demo-hr', '$2a$10$6es2.pcVQK85ODz0U/0FOe9iFoDogc8GzlmWt2K24Q3JkpXQ1y4/m', @demo_hr_employee, @demo_hr_department, TRUE),
       ('demo-manager', '$2a$10$6es2.pcVQK85ODz0U/0FOe9iFoDogc8GzlmWt2K24Q3JkpXQ1y4/m', @demo_manager_employee, @demo_ops_department, TRUE),
       ('demo-employee', '$2a$10$6es2.pcVQK85ODz0U/0FOe9iFoDogc8GzlmWt2K24Q3JkpXQ1y4/m', @demo_staff_employee, @demo_ops_department, TRUE)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), employee_id = VALUES(employee_id), department_id = VALUES(department_id), enabled = VALUES(enabled);

SET @demo_admin_user = (SELECT id FROM sys_user WHERE username = 'demo-admin' LIMIT 1);
SET @demo_hr_user = (SELECT id FROM sys_user WHERE username = 'demo-hr' LIMIT 1);
SET @demo_manager_user = (SELECT id FROM sys_user WHERE username = 'demo-manager' LIMIT 1);
SET @demo_employee_user = (SELECT id FROM sys_user WHERE username = 'demo-employee' LIMIT 1);

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT @demo_admin_user, id FROM sys_role WHERE code = 'SYSTEM_ADMIN';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT @demo_hr_user, id FROM sys_role WHERE code = 'HR_ADMIN';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT @demo_manager_user, id FROM sys_role WHERE code = 'DEPT_MANAGER';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT @demo_employee_user, id FROM sys_role WHERE code = 'EMPLOYEE';

INSERT INTO hr_request (request_no, request_type, employee_id, target_department_id, target_position_id,
                        applicant_id, payload_json, status, current_node, version_no)
VALUES ('DEMO-REQ-ONBOARD-001', 'ONBOARDING', @demo_staff_employee, @demo_ops_department, @demo_ops_staff_position,
        @demo_hr_user, JSON_OBJECT('source', 'demo', 'note', '脱敏演示入职申请'), 'APPROVED', NULL, 2),
       ('DEMO-REQ-TRANSFER-001', 'TRANSFER', @demo_staff_employee, @demo_hr_department, @demo_hr_position,
        @demo_manager_user, JSON_OBJECT('source', 'demo', 'note', '脱敏演示调动申请'), 'PENDING_DEPT_APPROVAL', 'DEPARTMENT', 1)
ON DUPLICATE KEY UPDATE status = VALUES(status), current_node = VALUES(current_node), version_no = VALUES(version_no);
