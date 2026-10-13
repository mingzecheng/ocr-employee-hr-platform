UPDATE sys_user
SET enabled = FALSE
WHERE username LIKE 'demo-%'
  AND LOWER('${demoAccountsEnabled}') <> 'true';

UPDATE employee
SET status = 'INACTIVE'
WHERE employee_no LIKE 'DEMO-EMP-%'
  AND LOWER('${demoAccountsEnabled}') <> 'true';

UPDATE org_position
SET enabled = FALSE
WHERE code LIKE 'DEMO-%'
  AND LOWER('${demoAccountsEnabled}') <> 'true';

UPDATE org_department
SET enabled = FALSE
WHERE code LIKE 'DEMO-%'
  AND LOWER('${demoAccountsEnabled}') <> 'true';
