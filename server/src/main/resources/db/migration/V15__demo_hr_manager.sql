-- Keep the demo workflow's department approval roles separate from HR processing.
SET @demo_hr_department = (SELECT id FROM org_department WHERE code = 'DEMO-HR' LIMIT 1);
SET @demo_hr_position = (SELECT id FROM org_position WHERE department_id = @demo_hr_department AND code = 'DEMO-HR-ADMIN' LIMIT 1);

INSERT INTO employee (employee_no, name, phone, department_id, position_id, status, hire_date)
VALUES ('DEMO-EMP-004', '演示人事负责人', '13800000004', @demo_hr_department, @demo_hr_position, 'ACTIVE', '2025-01-06')
ON DUPLICATE KEY UPDATE name = VALUES(name), department_id = VALUES(department_id),
                        position_id = VALUES(position_id), status = VALUES(status);

SET @demo_hr_manager_employee = (SELECT id FROM employee WHERE employee_no = 'DEMO-EMP-004' LIMIT 1);
UPDATE org_department SET manager_employee_id = @demo_hr_manager_employee
WHERE id = @demo_hr_department;

INSERT INTO sys_user (username, password_hash, employee_id, department_id, enabled)
VALUES ('demo-hr-manager', '$2a$10$6es2.pcVQK85ODz0U/0FOe9iFoDogc8GzlmWt2K24Q3JkpXQ1y4/m',
        @demo_hr_manager_employee, @demo_hr_department, TRUE)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), employee_id = VALUES(employee_id),
                        department_id = VALUES(department_id), enabled = VALUES(enabled);

SET @demo_hr_manager_user = (SELECT id FROM sys_user WHERE username = 'demo-hr-manager' LIMIT 1);
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT @demo_hr_manager_user, id FROM sys_role WHERE code = 'DEPT_MANAGER';
