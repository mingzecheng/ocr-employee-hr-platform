# 02 · 数据库表事实清单

> 来源：`server/src/main/resources/db/migration/V1__*.sql` ~ `V18__*.sql` 全量阅读。
> 数据库：MySQL 8.0（`hr_platform`），引擎 InnoDB，字符集 utf8mb4。
> 命名约定：主键 `id BIGINT AUTO_INCREMENT`；业务主键 `*_no VARCHAR`；时间戳 `created_at/updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP [ON UPDATE CURRENT_TIMESTAMP]`；逻辑删除用 `enabled BOOLEAN` 而非软删字段。
> 说明：本文件按迁移顺序逐表展开，最后附"全表速查表"。

---

## V1 · identity_organization（身份与组织）

### `sys_user` — 系统登录账号
| 字段 | 类型 | NULL | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT PK AI | NO | | |
| username | VARCHAR(64) UNIQUE | NO | | 登录名 |
| password_hash | VARCHAR(255) | NO | | BCrypt |
| employee_id | BIGINT | YES | | 关联 employee.id |
| department_id | BIGINT | YES | | 冗余部门（与员工主档一致） |
| enabled | BOOLEAN | NO | TRUE | 禁用后 JWT liveValidation 拒绝 |
| created_at / updated_at | TIMESTAMP | NO | CURRENT_TIMESTAMP | |

索引：`idx_user_department(department_id)`、`idx_user_employee(employee_id)`。

### `sys_role` — 角色
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| code | VARCHAR(32) UNIQUE | `EMPLOYEE / DEPT_MANAGER / HR_ADMIN / SYSTEM_ADMIN`（V1 seed） |
| name | VARCHAR(64) | 中文名 |
| enabled | BOOLEAN | 默认 TRUE |

### `sys_permission` — 权限点（**代码未读取**）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| code | VARCHAR(96) UNIQUE | |
| name | VARCHAR(128) | |
| enabled | BOOLEAN | |

### `sys_user_role` — 用户-角色（多对多）
复合 PK `(user_id, role_id)`；两个外键分别到 `sys_user.id`、`sys_role.id`。

### `sys_role_permission` — 角色-权限（**代码未读取**）
复合 PK `(role_id, permission_id)`；外键到 `sys_role.id`、`sys_permission.id`。

### `org_department` — 部门
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| code | VARCHAR(32) UNIQUE | |
| name | VARCHAR(128) | |
| parent_id | BIGINT NULL | 自引用外键 `fk_department_parent` |
| manager_employee_id | BIGINT NULL | 部门负责人（员工 id） |
| sort_order | INT | 默认 0 |
| enabled | BOOLEAN | 默认 TRUE |

索引：`idx_department_parent(parent_id)`。

### `org_position` — 岗位
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| department_id | BIGINT NOT NULL | FK → org_department.id |
| code | VARCHAR(32) | |
| name | VARCHAR(128) | |
| enabled | BOOLEAN | 默认 TRUE |

唯一键：`uk_position_department_code(department_id, code)`。

### `employee` — 员工主档
| 字段 | 类型 | NULL | 默认 | 说明 |
|---|---|---|---|---|
| id | BIGINT PK | NO | | |
| employee_no | VARCHAR(32) UNIQUE | NO | | 工号 |
| name | VARCHAR(128) | NO | | |
| phone | VARCHAR(32) | YES | | 手机号（HR 外脱敏） |
| department_id | BIGINT | NO | | FK → org_department.id |
| position_id | BIGINT | NO | | FK → org_position.id |
| status | VARCHAR(24) | NO | 'ACTIVE' | 枚举见 03 |
| hire_date | DATE | YES | | 入职日期 |
| leave_date | DATE | YES | | 离职日期 |

索引：`idx_employee_department_status(department_id, status)`。

---

## V2 · employee_archive_schema（档案与文件）

### `employee_status_history` — 员工状态/部门/岗位变更历史
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| employee_id | BIGINT NOT NULL | FK → employee.id |
| before_status / after_status | VARCHAR(24) | after 必填，before 可空 |
| before_department_id / after_department_id | BIGINT | |
| before_position_id / after_position_id | BIGINT | |
| source_request_id | BIGINT NULL | 关联触发本次变更的 hr_request.id |
| operator_id | BIGINT NULL | |
| created_at | TIMESTAMP | |

索引：`idx_employee_status_history(employee_id, created_at)`。

### `employee_archive` — 员工档案主表（1:1 员工）
| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| id | BIGINT PK | | |
| employee_id | BIGINT UNIQUE NOT NULL | | FK → employee.id |
| completeness | DECIMAL(5,2) | 0 | 完整度百分比 |
| confidentiality | VARCHAR(16) | 'INTERNAL' | 密级 |
| status | VARCHAR(24) | 'ACTIVE' | |

### `archive_document` — 档案文档类别（如身份证/学历/合同）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| employee_id | BIGINT NOT NULL | FK → employee.id |
| document_type | VARCHAR(64) | 业务类别码 |
| title | VARCHAR(128) | |

唯一键：`uk_archive_document_type(employee_id, document_type)`；索引 `idx_archive_document_employee(employee_id)`。

### `file_object` — 文件对象元数据（内容存 MinIO）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| object_key | VARCHAR(512) UNIQUE | MinIO object key |
| original_name | VARCHAR(255) | |
| content_type | VARCHAR(128) | |
| byte_size | BIGINT | |
| sha256 | CHAR(64) | 完整性校验/去重 |

索引：`idx_file_sha256(sha256)`。

### `archive_version` — 档案文档版本
| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| id | BIGINT PK | | |
| document_id | BIGINT NOT NULL | | FK → archive_document.id |
| version_no | INT NOT NULL | | |
| file_object_id | BIGINT NOT NULL | | FK → file_object.id |
| original_name | VARCHAR(255) | | |
| content_type | VARCHAR(128) | | |
| byte_size | BIGINT | | |
| sha256 | CHAR(64) | | |
| status | VARCHAR(24) | 'UPLOADED' | 版本状态 |
| is_current | BOOLEAN | TRUE | 是否当前版本 |
| created_by | BIGINT NOT NULL | | 上传人 sys_user.id |
| change_reason | VARCHAR(255) | | |

唯一键：`uk_document_version(document_id, version_no)`；索引 `idx_archive_current(document_id, is_current)`。

### `operation_log` — 操作审计日志
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| operator_id | BIGINT NULL | |
| action | VARCHAR(64) | 如 `CREATE_EMPLOYEE / REQUEST_FAILED / BOOTSTRAP_CREATE` |
| object_type | VARCHAR(64) | 如 `EMPLOYEE / HR_REQUEST / API_REQUEST / SYS_USER` |
| object_id | BIGINT NULL | |
| result | VARCHAR(32) | `SUCCESS / FAILED` |
| before_summary / after_summary | TEXT NULL | |
| trace_id | VARCHAR(64) NULL | |
| created_at | TIMESTAMP | |

索引：`idx_operation_object_time(object_type, object_id, created_at)`、`idx_operation_operator_time(operator_id, created_at)`。

---

## V3 · ocr_binding_schema

### `ocr_binding` — OCR 任务与档案版本绑定（1:1 版本）
| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| id | BIGINT PK | | |
| archive_version_id | BIGINT NOT NULL | | FK → archive_version.id |
| task_id | VARCHAR(128) NULL | | OCR 服务侧 UUID |
| document_type | VARCHAR(64) | | 如 `employee_profile` |
| status | VARCHAR(24) | 'PENDING' | 枚举见 03 |
| engine_version | VARCHAR(128) NULL | | PaddleOCR 版本 |
| processing_duration_ms | BIGINT | 0 | 耗时 |
| review_required | BOOLEAN | FALSE | 是否有待复核字段 |
| error_code / error_message | VARCHAR(64)/VARCHAR(512) NULL | | 失败原因 |
| preview_url / preview_content_type / preview_size / preview_sha256 / preview_width / preview_height | | | 检测框效果图元数据 |

唯一键：`uk_ocr_binding_version(archive_version_id)`、`uk_ocr_binding_task(task_id)`；索引 `idx_ocr_binding_status_time(status, updated_at)`。

### `ocr_field_revision` — OCR 字段人工修订历史
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| binding_id | BIGINT NOT NULL | FK → ocr_binding.id |
| field_code | VARCHAR(96) | 如 `employee_name/employee_id/id_number/phone` |
| previous_value | VARCHAR(512) NULL | |
| current_value | VARCHAR(512) NOT NULL | |
| reason | VARCHAR(512) NULL | |
| operator_id | BIGINT NULL | |
| created_at | TIMESTAMP | |

索引：`idx_ocr_revision_binding_field_time(binding_id, field_code, created_at)`。

> **注意**：OCR 识别出的字段值（`text_blocks`、`fields`）并不写 MySQL，而是存在 OCR 服务的 SQLite `ocr_tasks.payload` 中（见 05）；MySQL 侧只存绑定关系、状态、预览元数据与人工修订记录。

---

## V4 · workflow_schema（人事审批流）

### `hr_request` — 人事申请主表
| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| id | BIGINT PK | | |
| request_no | VARCHAR(64) UNIQUE | | 业务单号 |
| request_type | VARCHAR(24) | | `ONBOARDING / TRANSFER / LEAVE / OFFBOARDING` |
| employee_id | BIGINT NOT NULL | | FK → employee.id |
| target_department_id | BIGINT NULL | | 调动/入职目标部门 |
| target_position_id | BIGINT NULL | | 调动/入职目标岗位 |
| approval_department_id | BIGINT NOT NULL | | V9 新增；审批归属部门 |
| applicant_id | BIGINT NOT NULL | | 申请人 sys_user.id |
| source_version_id | BIGINT NULL | | 关联档案版本（如身份证扫描件） |
| payload_json | JSON NULL | | 扩展载荷 |
| status | VARCHAR(32) | 'DRAFT' | 枚举见 03 |
| current_node | VARCHAR(24) NULL | | `DEPARTMENT / HR` |
| version_no | INT | 0 | 乐观锁 |
| submitted_at / completed_at / employee_change_applied_at | TIMESTAMP NULL | | V11 新增 `employee_change_applied_at` |
| created_at / updated_at | TIMESTAMP | | |

索引：`idx_hr_request_status_type(status, request_type, created_at)`、`idx_hr_request_employee_status(employee_id, status)`、V9 `idx_hr_request_approval_department(approval_department_id, status, created_at)`、V11 `idx_hr_request_due_employee_change(status, employee_change_applied_at, request_type)`。

### `onboarding_detail` — 入职申请扩展
PK `request_id` FK → hr_request.id；字段：`hire_date DATE`、`contract_type VARCHAR(32)`。

### `transfer_detail` — 调动申请扩展
PK `request_id`；字段：`from_department_id`、`from_position_id`、`effective_date DATE`、`reason VARCHAR(512)`。

### `offboarding_detail` — 离职申请扩展
PK `request_id`；字段：`leave_date DATE`、`handover_person VARCHAR(128)`、`handover_status VARCHAR(32)`、`reason VARCHAR(512)`。

### `approval_record` — 审批节点记录
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| request_id | BIGINT NOT NULL | FK → hr_request.id |
| node_code | VARCHAR(24) | `DEPARTMENT / HR` |
| approver_id | BIGINT NOT NULL | |
| decision | VARCHAR(16) | `APPROVED / REJECTED` |
| comment | VARCHAR(512) NULL | |
| decided_at | TIMESTAMP | |

唯一键：`uk_approval_request_node(request_id, node_code)`（每个节点只审一次）。

---

## V5 · access_inventory_schema（档案借用与盘点）

### `archive_access_application` — 档案借用申请
| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| id | BIGINT PK | | |
| application_no | VARCHAR(64) UNIQUE | | |
| applicant_id | BIGINT NOT NULL | | FK → sys_user.id（V10 补外键） |
| department_id | BIGINT NULL | | |
| purpose | VARCHAR(512) | | 借用用途 |
| use_type | VARCHAR(32) | | 借用类型 |
| start_at / due_at | DATETIME | | V13 改为 NOT NULL |
| status | VARCHAR(24) | 'DRAFT' | 枚举见 03 |
| version_no | INT | 0 | 乐观锁 |

索引：`idx_access_status_due(status, due_at)`、`idx_access_applicant(applicant_id, created_at)`。

### `archive_access_item` — 申请明细（本次借用哪些档案版本）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| application_id | BIGINT NOT NULL | FK → archive_access_application.id |
| archive_version_id | BIGINT NOT NULL | FK → archive_version.id |
| employee_id | BIGINT NOT NULL | FK → employee.id（冗余，便于数据范围过滤） |
| department_id | BIGINT | |
| status | VARCHAR(24) | 'REQUESTED' | 明细状态 |

唯一键：`uk_access_application_version(application_id, archive_version_id)`。

### `archive_use_record` — 使用/归还流水
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| application_id | BIGINT NOT NULL | FK → archive_access_application.id |
| operator_id | BIGINT NOT NULL | |
| used_at | TIMESTAMP NOT NULL | |
| returned_at | TIMESTAMP NULL | |
| return_status | VARCHAR(24) NULL | |
| remark | VARCHAR(512) NULL | |
| returned_by_user_id | BIGINT NULL | V14 新增，FK → sys_user.id |
| handover_to_employee_id | BIGINT NULL | V14 新增，FK → employee.id（异常归还交接给的人） |

索引：`idx_access_use_active(application_id, returned_at)`、V14 `idx_access_use_handover_employee(handover_to_employee_id, returned_at)`。

### `inventory_task` — 盘点任务
| 字段 | 类型 | 默认 |
|---|---|---|
| id | BIGINT PK | |
| task_no | VARCHAR(64) UNIQUE | |
| initiator_id | BIGINT NOT NULL | |
| status | VARCHAR(24) | 'DRAFT' |
| created_at / completed_at | TIMESTAMP | |

索引：`idx_inventory_status_time(status, created_at)`。

### `inventory_item` — 盘点项（每份档案版本一条）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| task_id | BIGINT NOT NULL | FK → inventory_task.id |
| archive_version_id | BIGINT NOT NULL | FK → archive_version.id（被盘点的"期望版本"） |
| employee_id / department_id | BIGINT | 冗余 |
| actual_version_id | BIGINT NULL | 盘点时实际版本 |
| actual_status | VARCHAR(24) NULL | |
| difference_type | VARCHAR(32) NULL | 差异类型 |
| difference_description | VARCHAR(512) NULL | |
| checked_by | BIGINT NULL | |
| checked_at | TIMESTAMP NULL | |
| resolution_status | VARCHAR(24) | V12 新增，默认 'PENDING'；取值 `PENDING / OPEN / NOT_REQUIRED / RESOLVED` |
| resolution | VARCHAR(512) NULL | V12 闭环说明 |

### `exception_record` — 盘点异常记录
| 字段 | 类型 | 默认 |
|---|---|---|
| id | BIGINT PK | |
| inventory_item_id | BIGINT NOT NULL | FK → inventory_item.id |
| exception_type | VARCHAR(32) | |
| description | VARCHAR(512) | |
| status | VARCHAR(24) | 'OPEN'；闭环后 'RESOLVED' |
| resolved_by | BIGINT NULL | |
| resolved_at | TIMESTAMP NULL | |
| resolution | VARCHAR(512) NULL | |

索引 V12：`idx_exception_inventory_status(inventory_item_id, status)`。

---

## V6 · demo_seed（演示数据，非结构）
插入演示部门 `DEMO-HR/DEMO-OPS`、岗位、员工 `DEMO-EMP-001..003`、账号 `demo-admin/demo-hr/demo-manager/demo-employee`（密码均为 `password` 的 BCrypt），及两条示例 `hr_request`。

## V7 · contract_schema

### `employee_contract` — 员工劳动合同
| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| id | BIGINT PK | | |
| contract_no | VARCHAR(64) UNIQUE | | |
| employee_id | BIGINT NOT NULL | | FK → employee.id |
| contract_type | VARCHAR(32) | | 前端类型：`FIXED_TERM / OPEN_ENDED / INTERN` |
| signed_on / starts_on / ends_on | DATE NOT NULL | | CHECK `ends_on>=starts_on>=signed_on` |
| renewal_status | VARCHAR(24) | 'NOT_DUE' | CHECK IN (`NOT_DUE, RENEWED, NOT_RENEWING`)；前端另显示派生 `DUE` |
| source_version_id | BIGINT NULL | | FK → archive_version.id（合同扫描件） |
| renewed_from_contract_id | BIGINT NULL | | 自引用 FK |
| notes | VARCHAR(512) NULL | | |
| version_no | INT | 0 | |
| created_by / updated_by | BIGINT NOT NULL | | FK → sys_user.id |

索引：`idx_contract_employee_dates(employee_id, starts_on, ends_on)`、`idx_contract_renewal_end(renewal_status, ends_on)`。

## V8 · leave_request_detail

### `leave_request_detail` — 请假申请扩展
PK `request_id` FK → hr_request.id；字段：
- `leave_type VARCHAR(24) NOT NULL`，CHECK IN (`ANNUAL, SICK, PERSONAL, OTHER`)
- `start_date / end_date DATE NOT NULL`，CHECK `end_date>=start_date`
- `reason VARCHAR(512) NOT NULL`

## V9 · workflow_approval_department
`hr_request` 新增 `approval_department_id BIGINT NOT NULL`（V9 先加可空列并回填，再 MODIFY 为 NOT NULL），FK → org_department.id。

## V10 · archive_access_applicant_relation
为 `archive_access_application.applicant_id` 补 FK → sys_user.id。

## V11 · employee_change_effective_date
`hr_request` 新增 `employee_change_applied_at TIMESTAMP NULL`；已 APPROVED 记录回填 `COALESCE(completed_at, created_at, NOW())`。索引 `idx_hr_request_due_employee_change(status, employee_change_applied_at, request_type)`。

## V12 · inventory_exception_closure
`inventory_item` 新增 `resolution_status`、`resolution`；存量数据按 actual_status/difference_type/exception_record.status 回填；为 OPEN 项补写 exception_record。

## V13 · archive_access_datetime_range
`archive_access_application.start_at/due_at` MODIFY 为 DATETIME NOT NULL。

## V14 · archive_use_handover_tracking
`archive_use_record` 新增 `returned_by_user_id`、`handover_to_employee_id` 及对应 FK。

## V15 · demo_hr_manager
插入演示"人事负责人"员工与账号 `demo-hr-manager`（DEPT_MANAGER 角色，挂在 DEMO-HR 部门）。

## V16 · disable_demo_data_outside_demo_profile
当 `${demoAccountsEnabled}` != true 时：禁用所有 `demo-*` 账号、`DEMO-EMP-*` 员工置 INACTIVE、`DEMO-*` 部门/岗位禁用。

## V17 · archive_access_resolution_history

### `archive_access_resolution` — 异常归还闭环记录
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| application_id | BIGINT NOT NULL | FK → archive_access_application.id |
| resolved_by_user_id | BIGINT NOT NULL | FK → sys_user.id |
| resolution_note | VARCHAR(512) NOT NULL | |
| resolved_at | TIMESTAMP | |

索引：`idx_access_resolution_application(application_id, resolved_at, id)`。

## V18 · archive_access_decision_history

### `archive_access_decision` — 审批决策历史
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| application_id | BIGINT NOT NULL | FK → archive_access_application.id |
| decided_by_user_id | BIGINT NOT NULL | FK → sys_user.id |
| decision | VARCHAR(16) NOT NULL | CHECK IN (`APPROVED, REJECTED`) |
| decision_note | VARCHAR(512) NULL | |
| decided_at | TIMESTAMP | |

索引：`idx_access_decision_application(application_id, decided_at, id)`。

---

## 全表清单速查表

| # | 表名 | 所属领域 | 建表迁移 | 核心状态字段 |
|---|---|---|---|---|
| 1 | sys_user | 身份 | V1 | enabled |
| 2 | sys_role | 身份 | V1 | enabled |
| 3 | sys_permission | 身份（未用） | V1 | enabled |
| 4 | sys_user_role | 身份 | V1 | — |
| 5 | sys_role_permission | 身份（未用） | V1 | — |
| 6 | org_department | 组织 | V1 | enabled |
| 7 | org_position | 组织 | V1 | enabled |
| 8 | employee | 员工 | V1 | status |
| 9 | employee_status_history | 员工 | V2 | after_status |
| 10 | employee_archive | 档案 | V2 | status / confidentiality |
| 11 | archive_document | 档案 | V2 | — |
| 12 | file_object | 档案 | V2 | — |
| 13 | archive_version | 档案 | V2 | status / is_current |
| 14 | operation_log | 审计 | V2 | result |
| 15 | ocr_binding | OCR | V3 | status / review_required |
| 16 | ocr_field_revision | OCR | V3 | — |
| 17 | hr_request | 审批流 | V4 (+V9/V11) | status / current_node |
| 18 | onboarding_detail | 审批流 | V4 | — |
| 19 | transfer_detail | 审批流 | V4 | — |
| 20 | offboarding_detail | 审批流 | V4 | handover_status |
| 21 | approval_record | 审批流 | V4 | decision |
| 22 | archive_access_application | 档案借用 | V5 (+V10/V13) | status |
| 23 | archive_access_item | 档案借用 | V5 | status |
| 24 | archive_use_record | 档案借用 | V5 (+V14) | return_status |
| 25 | inventory_task | 盘点 | V5 | status |
| 26 | inventory_item | 盘点 | V5 (+V12) | actual_status / resolution_status |
| 27 | exception_record | 盘点 | V5 (+V12) | status |
| 28 | employee_contract | 合同 | V7 | status / renewal_status |
| 29 | leave_request_detail | 审批流 | V8 | leave_type |
| 30 | archive_access_resolution | 档案借用 | V17 | — |
| 31 | archive_access_decision | 档案借用 | V18 | decision |

**补充事实**：
- OCR 服务侧另有独立 SQLite 库 `ocr_tasks.sqlite3`（表 `ocr_tasks`、`field_revisions`），与 MySQL 不共享事务，详见 05。
- `business_field` 表由 `BusinessFieldMapper` 在代码中引用，但不在 Flyway V1–V18 中——它是 OCR 字段字典表，需运行时由 `OcrService`/迁移脚本初始化（**本次未在 SQL 中找到建表语句**，标记为"待确认"）。
