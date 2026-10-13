# 统计指标口径与可复算说明

> 本文档把 `GET /api/statistics/overview` 和 `GET /api/statistics/report` 背后的每个指标写成可复算表，答辩时可直接对照 SQL 重算。
> 代码基线：`server/src/main/java/com/hrplatform/statistics/StatisticsMapper.xml`。

## 0. 通用约定

| 约定项 | 取值 | 依据 |
|---|---|---|
| 时区 | `Asia/Shanghai` | `application.yml` JDBC URL `serverTimezone=Asia/Shanghai` |
| 时间范围参数 | `from`（LocalDate，含）、`to`（LocalDate，含当天） | SQL: `created_at >= #{from}` AND `created_at < DATE_ADD(#{to}, INTERVAL 1 DAY)` |
| 不传 from/to | 全量 | 不加 WHERE 时间条件 |
| 权限范围三档 | `ALL`（HR_ADMIN / SYSTEM_ADMIN）、`DEPARTMENT`（部门负责人）、`EMPLOYEE`（员工本人） | `DataScope` 由 JwtPrincipal 派生 |
| 空数据返回 | 数值 0、列表空数组 | `StatisticsRaw.empty()` + `COALESCE(..., 0)` |
| 取消/驳回是否计入 | **计入**总数（`workflow_count`），不计入 pending | `workflow_count` 无 status 过滤；`pending_workflow_count` 只计 `status LIKE 'PENDING%'` |
| 已完成流程周期 | 只统计 `completed_at IS NOT NULL` 的请求 | `workflowCycle` SQL WHERE 子句 |

---

## 1. Overview 指标（`GET /api/statistics/overview`）

| 指标 | 字段 | 计算口径 | 涉及表 | 取消/驳回 | 空数据 |
|---|---|---|---|---|---|
| 员工总数 | `employeeCount` | `COUNT(*) FROM employee` 按 DataScope 过滤 | employee | — | 0 |
| 档案完整数 | `completeArchiveCount` | `COUNT(*) FROM employee_archive ea JOIN employee e`，条件 `ea.completeness >= 100` | employee_archive, employee | — | 0 |
| 档案总数 | `archiveCount` | `COUNT(*) FROM employee_archive ea JOIN employee e`（无 completeness 过滤） | employee_archive, employee | — | 0 |
| 档案完整率 | `archiveCompleteness` | `completeArchiveCount / archiveCount`；分母为 0 时返回 0.0 | 派生 | — | 0.0 |
| 人事申请总数 | `workflowCount` | `COUNT(*) FROM hr_request r JOIN employee e`，无 status 过滤 | hr_request, employee | **计入** | 0 |
| 待处理申请数 | `pendingWorkflowCount` | `COUNT(*) ... WHERE r.status LIKE 'PENDING%'` | hr_request, employee | 不计入 | 0 |
| 在用档案借用 | `activeAccessCount` | `COUNT(*) FROM archive_access_application a WHERE a.status = 'IN_USE'` | archive_access_application | — | 0 |
| 异常档案借用 | `abnormalAccessCount` | `COUNT(*) ... WHERE a.status = 'ABNORMAL'` | archive_access_application | — | 0 |
| OCR 成功数 | `ocrSucceededCount` | `COUNT(*) FROM ocr_binding b ... WHERE b.status = 'SUCCEEDED'` | ocr_binding → archive_version → archive_document → employee | — | 0 |
| OCR 失败数 | `ocrFailedCount` | `COUNT(*) ... WHERE b.status = 'FAILED'` | 同上 | — | 0 |
| OCR 待复核数 | `ocrReviewCount` | `COUNT(*) ... WHERE b.status = 'REVIEWED'`（已识别待人工修订） | 同上 | — | 0 |
| OCR 平均耗时(ms) | `ocrAvgDurationMs` | `ocrDurationTotalMs / (ocrSucceededCount + ocrFailedCount)`；分母为 0 时 0.0 | 派生 | 只计 SUCCEEDED+FAILED | 0.0 |

---

## 2. Report 指标（`GET /api/statistics/report`）

### 2.1 月度趋势 `trend`

返回 `List<StatisticsTrendPoint>`，按 `YYYY-MM` 分组。

| 字段 | 口径 |
|---|---|
| `period` | `DATE_FORMAT(created_at, '%Y-%m')` |
| `employeeCount` | 该月新增员工数（`employee.created_at` 在该月） |
| `workflowCount` | 该月新建人事申请数（`hr_request.created_at` 在该月） |
| `ocrSucceededCount` | 该月 SUCCEEDED 的 OCR 任务数（`ocr_binding.created_at` 在该月） |
| `ocrFailedCount` | 该月 FAILED 的 OCR 任务数 |

**实现方式**：CTE `periods` 用 UNION 收集三张表的月份，再 LEFT JOIN 回各表聚合。空月份也会出现（CTE 保证），但无数据时计数为 0。

### 2.2 部门聚合 `departments`

返回 `List<DepartmentStatisticsRow>`，按员工数降序。

| 字段 | 口径 |
|---|---|
| `departmentId/name` | `org_department` 主数据 |
| `employeeCount` | `COUNT(DISTINCT e.id)`，该部门在职员工数 |
| `archiveCount` | `COUNT(DISTINCT ea.id)`，该部门员工的档案总数 |
| `workflowCount` | `COUNT(DISTINCT r.id)`，该部门员工发起的人事申请总数 |
| `pendingWorkflowCount` | `COUNT(DISTINCT CASE WHEN r.status LIKE 'PENDING%' THEN r.id END)` |
| `abnormalAccessCount` | `COUNT(DISTINCT CASE WHEN a.status = 'ABNORMAL' THEN a.id END)` |

**注意**：只返回 `d.enabled = TRUE` 的部门；无员工/无档案的部门也返回（LEFT JOIN），计数为 0。

### 2.3 流程周期 `cycle`

| 字段 | 口径 |
|---|---|
| `completedCount` | `COUNT(*) WHERE completed_at IS NOT NULL` 的已完成申请数 |
| `averageDurationHours` | `AVG(TIMESTAMPDIFF(SECOND, created_at, completed_at)) / 3600` |
| `shortestDurationHours` | `MIN(...) / 3600` |
| `longestDurationHours` | `MAX(...) / 3600` |

**关键边界**：
- 只统计 `completed_at IS NOT NULL` 的请求（即 APPROVED 或 REJECTED 的终态）
- 进行中、已撤回的请求不计入周期
- 空结果集时返回 0（COALESCE）
- 单位为**小时**（秒数差 / 3600），保留 double

---

## 3. 权限范围与数据可见性

| 角色 | DataScope 类型 | 可见数据 |
|---|---|---|
| 员工（EMPLOYEE） | `EMPLOYEE` | 本人档案、本人发起的申请、本人的借用记录 |
| 部门负责人（DEPT_MANAGER） | `DEPARTMENT` | 本部门所有员工、本部门发起的申请、本部门的借用 |
| 人事管理员（HR_ADMIN） | `ALL` | 全企业 |
| 系统管理员（SYSTEM_ADMIN） | `ALL`（强制） | 全企业统计，但不读档案明细 |

**注意**：SystemAdmin 在 Controller 里被强制设为 `ALL` scope（即使 JwtPrincipal.dataScope() 不是 ALL）。

---

## 4. 重算示例

以 HR 管理员查 2026 年 1 月的 overview 为例：

```sql
-- 员工数
SELECT COUNT(*) FROM employee
WHERE created_at >= '2026-01-01' AND created_at < '2026-02-01';

-- 人事申请数
SELECT COUNT(*) FROM hr_request r JOIN employee e ON e.id = r.employee_id
WHERE r.created_at >= '2026-01-01' AND r.created_at < '2026-02-01';

-- 待处理数
SELECT COUNT(*) FROM hr_request r JOIN employee e ON e.id = r.employee_id
WHERE r.status LIKE 'PENDING%'
  AND r.created_at >= '2026-01-01' AND r.created_at < '2026-02-01';

-- 流程周期（1月已完成的申请，平均耗时小时）
SELECT AVG(TIMESTAMPDIFF(SECOND, r.created_at, r.completed_at)) / 3600
FROM hr_request r
WHERE r.completed_at IS NOT NULL
  AND r.created_at >= '2026-01-01' AND r.created_at < '2026-02-01';
```

---

## 5. 已知边界与限制

1. **`pending_workflow_count` 用 `LIKE 'PENDING%'`**：匹配 `PENDING_DEPT_APPROVAL`、`PENDING_HR_APPROVAL` 等所有待审批状态，但如果未来新增 `PENDING_XXX` 状态会自动纳入。
2. **OCR 耗时**：`ocr_duration_total_ms` 来自 OCR 服务侧 SQLite 汇总，MySQL 只存摘要；两库最终一致，重启后可能短暂不一致。
3. **部门聚合的 LEFT JOIN**：如果一个部门有 100 个员工、每人 5 份档案、每人 3 条申请，JOIN 后行数是笛卡尔积；代码用 `COUNT(DISTINCT ...)` 规避，但性能在大数据量下需优化。
4. **时间范围边界**：`to` 含当天（用 `< DATE_ADD(to, INTERVAL 1 DAY)` 实现），答辩时要说明"含当天 23:59:59"。
5. **取消/驳回的申请计入 workflow_count 但不计入周期**：这是有意设计——驳回也是一次流程流转，但不产生"完成周期"。
