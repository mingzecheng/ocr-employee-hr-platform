-- V22: Realistic company organization seed.
-- ---------------------------------------------------------------------------
-- Purpose: Replace the placeholder "演示* / DEMO-VERIFY-*" org with a believable
--   company hierarchy (总经办 -> 技术/产品/人力/财务/市场/销售/运营 -> 二级组).
-- Scope:
--   * DEMO profile ONLY: the whole file is gated by the Flyway placeholder
--     `${demoAccountsEnabled}` (default true; forced to false in application-production.yml).
--     When it is not 'true' nothing below inserts or mutates.
--   * IDEMPOTENT: uses INSERT IGNORE over a derived staging set, keyed by natural
--     keys (department code / position department+code / employee employee_no).
--     Re-running the migration is a no-op.
--   * NON-DESTRUCTIVE: existing demo rows (org_department id 1-13, old employees)
--     are left untouched. New data uses id 20+ (departments), 50+ (positions),
--     200+ (employees) which are free in a fresh demo DB.
--   * New employees are pure 档案 records: they do NOT get sys_user login accounts.
-- utf8mb4 Chinese throughout.
-- ---------------------------------------------------------------------------

-- 1) Departments: 1 总经办 + 6 一级部门 + 18 二级组 = 25 rows (id 20-44).
--    Listed in id-ascending order so a parent row always exists before its children
--    are inserted in the same multi-row statement (immediate InnoDB FK check).
INSERT IGNORE INTO org_department (id, code, name, parent_id, sort_order, enabled)
SELECT t.id, t.code, t.name, t.parent_id, t.sort_order, TRUE
FROM (
    SELECT 20 AS id, 'EXEC-CEO'        AS code, '总经办'       AS name, CAST(NULL AS UNSIGNED) AS parent_id, 10  AS sort_order
    UNION ALL SELECT 21, 'TECH',          '技术中心',       20, 20
    UNION ALL SELECT 22, 'TECH-FE',        '前端组',         21, 21
    UNION ALL SELECT 23, 'TECH-BE',       '后端组',         21, 22
    UNION ALL SELECT 24, 'TECH-QA',       '测试组',         21, 23
    UNION ALL SELECT 25, 'TECH-INFRA',    '运维组',         21, 24
    UNION ALL SELECT 26, 'PRODUCT',       '产品中心',       20, 30
    UNION ALL SELECT 27, 'PRODUCT-DESIGN', '产品设计组',     26, 31
    UNION ALL SELECT 28, 'PRODUCT-UI',   'UI设计组',       26, 32
    UNION ALL SELECT 29, 'HR',            '人力资源部',     20, 40
    UNION ALL SELECT 30, 'HR-RECRUIT',    '招聘组',         29, 41
    UNION ALL SELECT 31, 'HR-CB',         '薪酬绩效组',     29, 42
    UNION ALL SELECT 32, 'HR-ER',         '员工关系组',     29, 43
    UNION ALL SELECT 33, 'FIN',           '财务部',         20, 50
    UNION ALL SELECT 34, 'FIN-ACCOUNT',   '会计组',         33, 51
    UNION ALL SELECT 35, 'FIN-CASHIER',   '出纳组',         33, 52
    UNION ALL SELECT 36, 'MKT',           '市场部',         20, 60
    UNION ALL SELECT 37, 'MKT-BRAND',     '品牌组',         36, 61
    UNION ALL SELECT 38, 'MKT-CHANNEL',   '渠道组',         36, 62
    UNION ALL SELECT 39, 'SALES',         '销售部',         20, 70
    UNION ALL SELECT 40, 'SALES-KA',      '大客户组',       39, 71
    UNION ALL SELECT 41, 'SALES-SMB',     '中小客户组',     39, 72
    UNION ALL SELECT 42, 'OPS',           '运营部',         20, 80
    UNION ALL SELECT 43, 'OPS-USER',      '用户运营组',     42, 81
    UNION ALL SELECT 44, 'OPS-CONTENT',   '内容运营组',     42, 82
) t
WHERE LOWER('${demoAccountsEnabled}') = 'true';

-- 2) Positions: one 负责人 per 一级部门 + one 专员 per 二级组 + one 架构师 (id 50-75, 26 rows).
INSERT IGNORE INTO org_position (id, department_id, code, name, enabled)
SELECT t.id, t.department_id, t.code, t.name, TRUE
FROM (
    SELECT 50 AS id, 20 AS department_id, 'POS-GM'        AS code, '总经理'         AS name
    UNION ALL SELECT 51, 21, 'POS-TECH-HEAD',   '技术中心负责人'
    UNION ALL SELECT 52, 22, 'POS-FE-ENG',      '前端开发工程师'
    UNION ALL SELECT 53, 23, 'POS-BE-ENG',      '后端开发工程师'
    UNION ALL SELECT 54, 23, 'POS-ARCHITECT',    '架构师'
    UNION ALL SELECT 55, 24, 'POS-QA-ENG',      '测试工程师'
    UNION ALL SELECT 56, 25, 'POS-INFRA-ENG',   '运维工程师'
    UNION ALL SELECT 57, 26, 'POS-PROD-HEAD',    '产品中心负责人'
    UNION ALL SELECT 58, 27, 'POS-PD-SPECIALIST','产品设计专员'
    UNION ALL SELECT 59, 28, 'POS-UI-DESIGNER', 'UI设计师'
    UNION ALL SELECT 60, 29, 'POS-HR-HEAD',     '人力资源部负责人'
    UNION ALL SELECT 61, 30, 'POS-RECRUITER',   '招聘专员'
    UNION ALL SELECT 62, 31, 'POS-CB-SPECIALIST','薪酬绩效专员'
    UNION ALL SELECT 63, 32, 'POS-ER-SPECIALIST','员工关系专员'
    UNION ALL SELECT 64, 33, 'POS-FIN-HEAD',    '财务部负责人'
    UNION ALL SELECT 65, 34, 'POS-ACCOUNTANT',  '会计'
    UNION ALL SELECT 66, 35, 'POS-CASHIER',     '出纳'
    UNION ALL SELECT 67, 36, 'POS-MKT-HEAD',    '市场部负责人'
    UNION ALL SELECT 68, 37, 'POS-BRAND-SPECIALIST','品牌专员'
    UNION ALL SELECT 69, 38, 'POS-CHANNEL-SPECIALIST','渠道专员'
    UNION ALL SELECT 70, 39, 'POS-SALES-HEAD',  '销售部负责人'
    UNION ALL SELECT 71, 40, 'POS-KA-MANAGER',  '大客户经理'
    UNION ALL SELECT 72, 41, 'POS-SMB-MANAGER', '中小客户经理'
    UNION ALL SELECT 73, 42, 'POS-OPS-HEAD',    '运营部负责人'
    UNION ALL SELECT 74, 43, 'POS-USER-OPS',    '用户运营专员'
    UNION ALL SELECT 75, 44, 'POS-CONTENT-OPS', '内容运营专员'
) t
WHERE LOWER('${demoAccountsEnabled}') = 'true';

-- 3) Employees: 30 档案 records (id 200-229, EMP-2026-001..030). No sys_user binding.
--    13800138xxx is a reserved test segment; all status ACTIVE.
INSERT IGNORE INTO employee (id, employee_no, name, phone, department_id, position_id, status, hire_date)
SELECT t.id, t.employee_no, t.name, t.phone, t.department_id, t.position_id, 'ACTIVE', t.hire_date
FROM (
    SELECT 200 AS id, 'EMP-2026-001' AS employee_no, '张三甲' AS name, '13800138001' AS phone, 20 AS department_id, 50 AS position_id, '2023-03-01' AS hire_date
    UNION ALL SELECT 201, 'EMP-2026-002', '李四乙', '13800138002', 21, 51, '2023-04-10'
    UNION ALL SELECT 202, 'EMP-2026-003', '王五福', '13800138003', 22, 52, '2024-02-19'
    UNION ALL SELECT 203, 'EMP-2026-004', '赵六丁', '13800138004', 22, 52, '2024-06-03'
    UNION ALL SELECT 204, 'EMP-2026-005', '陈六合', '13800138005', 23, 53, '2023-07-15'
    UNION ALL SELECT 205, 'EMP-2026-006', '周七七', '13800138006', 23, 54, '2022-09-01'
    UNION ALL SELECT 206, 'EMP-2026-007', '吴小八', '13800138007', 23, 53, '2024-03-11'
    UNION ALL SELECT 207, 'EMP-2026-008', '郑小九', '13800138008', 24, 55, '2024-04-22'
    UNION ALL SELECT 208, 'EMP-2026-009', '孙小小', '13800138009', 24, 55, '2024-08-05'
    UNION ALL SELECT 209, 'EMP-2026-010', '钱多多', '13800138010', 25, 56, '2023-11-20'
    UNION ALL SELECT 210, 'EMP-2026-011', '冯十一', '13800138011', 26, 57, '2023-05-08'
    UNION ALL SELECT 211, 'EMP-2026-012', '褚十二', '13800138012', 27, 58, '2024-01-15'
    UNION ALL SELECT 212, 'EMP-2026-013', '卫十三', '13800138013', 28, 59, '2024-05-27'
    UNION ALL SELECT 213, 'EMP-2026-014', '蒋十四', '13800138014', 29, 60, '2023-02-06'
    UNION ALL SELECT 214, 'EMP-2026-015', '沈十五', '13800138015', 30, 61, '2024-07-01'
    UNION ALL SELECT 215, 'EMP-2026-016', '韩十六', '13800138016', 31, 62, '2023-09-18'
    UNION ALL SELECT 216, 'EMP-2026-017', '杨十七', '13800138017', 32, 63, '2024-09-09'
    UNION ALL SELECT 217, 'EMP-2026-018', '朱十八', '13800138018', 33, 64, '2022-12-01'
    UNION ALL SELECT 218, 'EMP-2026-019', '秦十九', '13800138019', 34, 65, '2024-03-25'
    UNION ALL SELECT 219, 'EMP-2026-020', '尤二十', '13800138020', 35, 66, '2024-10-14'
    UNION ALL SELECT 220, 'EMP-2026-021', '孔廿一', '13800138021', 36, 67, '2023-06-12'
    UNION ALL SELECT 221, 'EMP-2026-022', '曹廿二', '13800138022', 37, 68, '2024-04-01'
    UNION ALL SELECT 222, 'EMP-2026-023', '严廿三', '13800138023', 38, 69, '2024-11-11'
    UNION ALL SELECT 223, 'EMP-2026-024', '华廿四', '13800138024', 39, 70, '2023-08-21'
    UNION ALL SELECT 224, 'EMP-2026-025', '金廿五', '13800138025', 40, 71, '2024-02-05'
    UNION ALL SELECT 225, 'EMP-2026-026', '魏廿六', '13800138026', 40, 71, '2024-12-02'
    UNION ALL SELECT 226, 'EMP-2026-027', '陶廿七', '13800138027', 41, 72, '2025-01-13'
    UNION ALL SELECT 227, 'EMP-2026-028', '姜廿八', '13800138028', 42, 73, '2023-10-30'
    UNION ALL SELECT 228, 'EMP-2026-029', '戚廿九', '13800138029', 43, 74, '2025-02-17'
    UNION ALL SELECT 229, 'EMP-2026-030', '谢三十', '13800138030', 44, 75, '2025-03-03'
) t
WHERE LOWER('${demoAccountsEnabled}') = 'true';

-- 4) Matching 档案 rows so the new employees behave like normal HR records (mirrors V6).
INSERT IGNORE INTO employee_archive (employee_id, completeness, confidentiality, status)
SELECT t.employee_id, t.completeness, 'INTERNAL', 'ACTIVE'
FROM (
    SELECT 200 AS employee_id, 95 AS completeness UNION ALL SELECT 201, 90
    UNION ALL SELECT 202, 70 UNION ALL SELECT 203, 60
    UNION ALL SELECT 204, 85 UNION ALL SELECT 205, 95
    UNION ALL SELECT 206, 65 UNION ALL SELECT 207, 55
    UNION ALL SELECT 208, 50 UNION ALL SELECT 209, 75
    UNION ALL SELECT 210, 88 UNION ALL SELECT 211, 68
    UNION ALL SELECT 212, 62 UNION ALL SELECT 213, 92
    UNION ALL SELECT 214, 58 UNION ALL SELECT 215, 72
    UNION ALL SELECT 216, 54 UNION ALL SELECT 217, 96
    UNION ALL SELECT 218, 78 UNION ALL SELECT 219, 52
    UNION ALL SELECT 220, 84 UNION ALL SELECT 221, 64
    UNION ALL SELECT 222, 48 UNION ALL SELECT 223, 86
    UNION ALL SELECT 224, 76 UNION ALL SELECT 225, 46
    UNION ALL SELECT 226, 44 UNION ALL SELECT 227, 82
    UNION ALL SELECT 228, 42 UNION ALL SELECT 229, 40
) t
WHERE LOWER('${demoAccountsEnabled}') = 'true';

-- 5) Wire department managers to the leader employees (gated; no-op outside demo).
UPDATE org_department SET manager_employee_id = 200 WHERE id = 20 AND LOWER('${demoAccountsEnabled}') = 'true';
UPDATE org_department SET manager_employee_id = 201 WHERE id = 21 AND LOWER('${demoAccountsEnabled}') = 'true';
UPDATE org_department SET manager_employee_id = 210 WHERE id = 26 AND LOWER('${demoAccountsEnabled}') = 'true';
UPDATE org_department SET manager_employee_id = 213 WHERE id = 29 AND LOWER('${demoAccountsEnabled}') = 'true';
UPDATE org_department SET manager_employee_id = 217 WHERE id = 33 AND LOWER('${demoAccountsEnabled}') = 'true';
UPDATE org_department SET manager_employee_id = 220 WHERE id = 36 AND LOWER('${demoAccountsEnabled}') = 'true';
UPDATE org_department SET manager_employee_id = 223 WHERE id = 39 AND LOWER('${demoAccountsEnabled}') = 'true';
UPDATE org_department SET manager_employee_id = 227 WHERE id = 42 AND LOWER('${demoAccountsEnabled}') = 'true';
