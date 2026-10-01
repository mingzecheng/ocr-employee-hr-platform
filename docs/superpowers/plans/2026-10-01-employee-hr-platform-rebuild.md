# 企业员工档案与人事流程管理平台重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留现有 `ocr-service` 的前提下，重建一个可运行、可测试、可演示的 Spring Boot + MyBatis + MySQL + Vue 员工档案与人事流程管理平台。

**Architecture:** 使用一个 Spring Boot 模块化单体承载认证、组织、员工、档案、OCR 关联、人事审批、档案授权、盘点、统计和审计。OCR 继续作为独立 FastAPI 服务，通过 HTTP 与 `taskId` 关联；文件二进制保存在 MinIO，业务事实保存在 MySQL。

**Tech Stack:** Java 17、Spring Boot 3.x、Spring Security 6、MyBatis、Flyway、MySQL 8、Redis 7、MinIO、Vue 3、Vite、TypeScript、Element Plus、现有 PaddleOCR FastAPI 服务。

## Global Constraints

- 只保留并复用现有 `ocr-service` 的 HTTP 契约和证据存储，不读取其 SQLite。
- 新业务平台只从 `server/` 构建；旧 `backend/`、`frontend/` 和旧业务编排移入 `legacy-reference/`，不参与构建。
- P0 必须覆盖认证、组织员工、档案版本、OCR、入职/调动/离职、档案访问/归还、盘点、统计和审计。
- 所有核心列表接口支持 `page`、`pageSize`、组合筛选和数据范围过滤。
- 原图、完整 OCR JSON、检测框 PNG、密码和 JWT 不写入 Redis 或操作日志。
- 状态变更、版本发布、审批、归还和异常处理在事务中完成，并留下不可覆盖的历史记录。
- 使用模拟或脱敏数据；不接入真实企业 HR、社保、薪资、电子签名或单点登录。

---

## Task 1: Repository Reset And New Project Skeleton

**Files:**
- Move: `backend/` -> `legacy-reference/backend/`
- Move: `frontend/` -> `legacy-reference/frontend/`
- Move: `infra/` -> `legacy-reference/infra/`
- Create: `server/pom.xml`
- Create: `server/src/main/java/com/hrplatform/PlatformApplication.java`
- Create: `server/src/main/resources/application.yml`
- Create: `server/src/test/java/com/hrplatform/PlatformApplicationTest.java`
- Create: `web/package.json`
- Create: `web/vite.config.ts`
- Create: `web/src/main.ts`
- Create: `web/src/App.vue`
- Create: `infra/docker-compose.yml`
- Create: `README.md`

**Interfaces:**
- Produces the `server` executable jar, the `web` Vite app, and local MySQL/Redis/MinIO services.
- Preserves `ocr-service/` at its current path and leaves its tests and runtime files untouched.

- [ ] **Step 1: Move old business implementation into a read-only reference directory**

Run:

```bash
mkdir -p legacy-reference
mv backend legacy-reference/backend
mv frontend legacy-reference/frontend
mv infra legacy-reference/infra
```

Expected: `server/`, `web/`, and `infra/` are available for the new implementation; `ocr-service/` is unchanged.

- [ ] **Step 2: Create the Maven parent and Spring Boot entry point**

`server/pom.xml` must define Java 17, Spring Boot 3.x, MyBatis Spring Boot starter, MySQL connector, Flyway, Spring Security, JWT library, validation, actuator, and test dependencies. `PlatformApplication` uses `@SpringBootApplication` and `@MapperScan("com.hrplatform")`.

- [ ] **Step 3: Add the first application configuration**

`server/src/main/resources/application.yml` binds `SERVER_PORT`, `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `OCR_BASE_URL`, `OCR_INTERNAL_TOKEN`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, and `JWT_SECRET` with local defaults that are safe for development.

- [ ] **Step 4: Add a boot smoke test**

```java
@SpringBootTest
class PlatformApplicationTest {
    @Test
    void applicationContextLoads() {
    }
}
```

Run: `mvn -f server/pom.xml test -Dtest=PlatformApplicationTest`  
Expected: the context test passes without requiring MySQL by using the test profile.

- [ ] **Step 5: Create the Vue shell and local infrastructure**

The Vue shell must render a login placeholder and use `/api` as the runtime base path. Compose must start MySQL 8, Redis 7, and MinIO with named volumes and health checks. Do not copy old service code into the new directories.

- [ ] **Step 6: Verify skeleton and commit**

Run:

```bash
mvn -f server/pom.xml test
cd web && npm install && npm run build
```

Expected: both commands exit with code 0. Commit with `git add server web infra README.md legacy-reference && git commit -m "chore: create rebuilt platform skeleton"`.

## Task 2: Common Web Layer, Authentication, Organization, And Database Baseline

**Files:**
- Create: `server/src/main/java/com/hrplatform/common/web/ApiResponse.java`
- Create: `server/src/main/java/com/hrplatform/common/web/GlobalExceptionHandler.java`
- Create: `server/src/main/java/com/hrplatform/common/web/TraceIdFilter.java`
- Create: `server/src/main/java/com/hrplatform/common/security/JwtService.java`
- Create: `server/src/main/java/com/hrplatform/common/security/SecurityConfiguration.java`
- Create: `server/src/main/java/com/hrplatform/auth/AuthController.java`
- Create: `server/src/main/java/com/hrplatform/auth/AuthService.java`
- Create: `server/src/main/java/com/hrplatform/organization/OrganizationController.java`
- Create: `server/src/main/java/com/hrplatform/organization/OrganizationMapper.java`
- Create: `server/src/main/resources/db/migration/V1__identity_organization.sql`
- Create: `server/src/test/java/com/hrplatform/auth/AuthServiceTest.java`
- Create: `server/src/test/java/com/hrplatform/common/security/DataScopeTest.java`
- Create: `server/src/test/resources/application-test.yml`

**Interfaces:**
- `POST /api/auth/login` consumes `{username,password}` and returns `{accessToken,expiresAt,user}`.
- `GET /api/auth/me` returns the current user and computed `dataScope`.
- `GET /api/departments/tree` returns nested departments.
- Roles are `EMPLOYEE`, `DEPT_MANAGER`, `HR_ADMIN`, and `SYSTEM_ADMIN`; data scopes are `EMPLOYEE`, `DEPARTMENT`, and `ALL`.

- [ ] **Step 1: Write failing authentication and scope tests**

Test the following exact cases: valid BCrypt password returns a token; invalid password returns `INVALID_CREDENTIALS`; `DEPT_MANAGER` produces `DEPARTMENT` scope; `SYSTEM_ADMIN` produces `ALL`; absent authentication produces `NONE`.

- [ ] **Step 2: Add V1 identity and organization schema**

Create `sys_user`, `sys_role`, `sys_permission`, `sys_user_role`, `sys_role_permission`, `org_department`, `org_position`, and `employee` tables with unique employee numbers, foreign keys, enabled flags, timestamps, and indexes for department and status. Seed the four roles, essential permissions, a root department, and a local system administrator from `IDENTITY_ADMIN_PASSWORD` through an idempotent startup seed.

- [ ] **Step 3: Implement common response and exception contracts**

`ApiResponse<T>` must contain `code`, `message`, `data`, and `traceId`. Map validation failures to `VALIDATION_ERROR`/400, authentication failures to `UNAUTHORIZED`/401, authorization failures to `FORBIDDEN`/403, and unknown failures to `INTERNAL_ERROR`/500 without exposing stack traces.

- [ ] **Step 4: Implement JWT authentication and method authorization**

Use a stateless bearer filter. `JwtService` signs and verifies user ID, employee ID, department ID, role codes, scope type, issue time, and expiry. Add `@PreAuthorize` checks at controllers and a reusable `DataScope` predicate for employee and department SQL.

- [ ] **Step 5: Implement login, current user, and department tree endpoints**

Use MyBatis XML for queries. Passwords are BCrypt hashes. The department tree query must return only enabled departments and stable child ordering.

- [ ] **Step 6: Run focused tests and commit**

Run: `mvn -f server/pom.xml test -Dtest=AuthServiceTest,DataScopeTest`  
Expected: all authentication, scope, validation, and exception tests pass. Commit with `git commit -am "feat: add authentication and organization foundation"`.

## Task 3: Employee, Archive, File Version, And Audit Modules

**Files:**
- Create: `server/src/main/resources/db/migration/V2__employee_archive_schema.sql`
- Create: `server/src/main/java/com/hrplatform/employee/EmployeeController.java`
- Create: `server/src/main/java/com/hrplatform/employee/EmployeeService.java`
- Create: `server/src/main/java/com/hrplatform/employee/EmployeeMapper.java`
- Create: `server/src/main/java/com/hrplatform/archive/ArchiveController.java`
- Create: `server/src/main/java/com/hrplatform/archive/ArchiveService.java`
- Create: `server/src/main/java/com/hrplatform/archive/ArchiveMapper.java`
- Create: `server/src/main/java/com/hrplatform/archive/ObjectStorage.java`
- Create: `server/src/main/java/com/hrplatform/audit/OperationLogService.java`
- Create: `server/src/test/java/com/hrplatform/employee/EmployeeServiceTest.java`
- Create: `server/src/test/java/com/hrplatform/archive/ArchiveServiceTest.java`
- Create: `server/src/test/java/com/hrplatform/archive/ArchiveDataScopeTest.java`

**Interfaces:**
- `GET /api/employees` accepts `keyword`, `departmentId`, `status`, `page`, and `pageSize`.
- `POST /api/employees` validates unique `employeeNo`, required name, department, and position.
- `POST /api/archive/employees/{employeeId}/documents` accepts PNG/JPEG multipart data and returns `documentId` and `versionId`.
- `GET /api/archive/employees/{employeeId}/documents` and `GET /api/archive/documents/{documentId}/versions` are scope-aware paginated reads.
- `GET /api/archive/versions/{versionId}/download` streams a checked object without accepting a client-supplied object key.

- [ ] **Step 1: Write failing employee and archive tests**

Cover unique employee number rejection, employee data scope, rejected file type, SHA-256 calculation, version number increment, old version preservation, cross-department `403`, and missing version `404`.

- [ ] **Step 2: Add the archive and audit schema**

Create `employee_status_history`, `employee_archive`, `archive_document`, `file_object`, `archive_version`, and `operation_log`. Add foreign keys, `(document_id,version_no)` uniqueness, current-version index, object hash index, and audit object/time indexes.

- [ ] **Step 3: Implement employee CRUD and history**

Employee writes run in transactions, update `employee_status_history` on status or organization changes, and write an operation log. Queries apply `DataScope` before pagination.

- [ ] **Step 4: Implement storage and archive versioning**

Define `ObjectStorage.put/get` with a MinIO implementation and an in-memory test double. Validate actual image bytes with `ImageIO`, calculate SHA-256, generate server-side object keys, and never expose private keys in DTOs. New versions are append-only; publishing switches `is_current` atomically.

- [ ] **Step 5: Implement controlled download and audit**

Perform scope-aware version lookup before object storage access. Log upload, download, version creation, publication, and failure with trace ID and result code.

- [ ] **Step 6: Run tests and commit**

Run: `mvn -f server/pom.xml test -Dtest=EmployeeServiceTest,ArchiveServiceTest,ArchiveDataScopeTest`  
Expected: all file, version, scope, and audit tests pass. Commit with `git commit -am "feat: add employee archive and version modules"`.

## Task 4: OCR HTTP Integration And Business Field Mapping

**Files:**
- Create: `server/src/main/java/com/hrplatform/ocr/OcrClient.java`
- Create: `server/src/main/java/com/hrplatform/ocr/HttpOcrClient.java`
- Create: `server/src/main/java/com/hrplatform/ocr/OcrDtos.java`
- Create: `server/src/main/java/com/hrplatform/ocr/OcrFieldTemplate.java`
- Create: `server/src/main/java/com/hrplatform/ocr/BusinessFieldMapper.java`
- Create: `server/src/main/java/com/hrplatform/ocr/OcrService.java`
- Create: `server/src/main/java/com/hrplatform/ocr/OcrController.java`
- Create: `server/src/main/resources/db/migration/V3__ocr_binding_schema.sql`
- Create: `server/src/test/java/com/hrplatform/ocr/BusinessFieldMapperTest.java`
- Create: `server/src/test/java/com/hrplatform/ocr/OcrClientContractTest.java`
- Create: `server/src/test/java/com/hrplatform/ocr/OcrServiceTest.java`

**Interfaces:**
- `POST /api/archive/versions/{versionId}/ocr` triggers an idempotent OCR task.
- `GET /api/archive/versions/{versionId}/ocr-result` returns binding status, text block summary, mapped fields, confidence and validation status.
- `GET /api/archive/ocr-bindings/{bindingId}/preview` proxies the PNG response.
- `PUT /api/archive/ocr-bindings/{bindingId}/fields/{fieldCode}` stores a business field revision and revalidates the field.

- [ ] **Step 1: Write mapper and HTTP contract tests**

Use WireMock or MockWebServer to verify `X-OCR-Internal-Token`, source object metadata, successful `SUCCEEDED`, `FAILED`, timeout, `SOURCE_OBJECT_NOT_FOUND`, and missing preview behavior. Test exact mappings for employee profile, onboarding, transfer, and offboarding labels.

- [ ] **Step 2: Add OCR binding and field revision schema**

Create `ocr_binding` with a unique `archive_version_id`, task ID, status, engine version, duration, summary JSON, preview metadata, error code, and timestamps. Create `ocr_field_revision` with previous/current values, reason, operator, and immutable timestamps.

- [ ] **Step 3: Implement the OCR client**

Use Spring `RestClient` with connect/read timeouts and explicit error mapping. Support file reference calls, task queries, preview proxying, and optional employee-profile correction calls. Do not deserialize or expose MinIO object keys from OCR responses.

- [ ] **Step 4: Implement business field templates and validation**

Map labels for the four document types. Validate employee numbers, ISO dates, phone numbers, required fields, and department/position existence. Mark confidence below `0.85` or failed validation as `REVIEW_REQUIRED` without deleting the value.

- [ ] **Step 5: Implement idempotent OCR service and revision history**

A second trigger for the same version returns the existing binding while it is processing or succeeded. OCR failure preserves the archive version and source file. Every field revision writes an operation log and never mutates the raw OCR text block.

- [ ] **Step 6: Run tests and commit**

Run: `mvn -f server/pom.xml test -Dtest=BusinessFieldMapperTest,OcrClientContractTest,OcrServiceTest`  
Expected: all contract, mapping, idempotency, failure, and revision tests pass. Commit with `git commit -am "feat: integrate OCR evidence and field review"`.

## Task 5: HR Requests And Fixed Two-Level Approval

**Files:**
- Create: `server/src/main/resources/db/migration/V4__workflow_schema.sql`
- Create: `server/src/main/java/com/hrplatform/workflow/RequestController.java`
- Create: `server/src/main/java/com/hrplatform/workflow/RequestService.java`
- Create: `server/src/main/java/com/hrplatform/workflow/RequestMapper.java`
- Create: `server/src/main/java/com/hrplatform/workflow/RequestStateMachine.java`
- Create: `server/src/test/java/com/hrplatform/workflow/RequestStateMachineTest.java`
- Create: `server/src/test/java/com/hrplatform/workflow/RequestServiceTest.java`

**Interfaces:**
- `POST /api/hr-requests` creates onboarding, transfer, or offboarding draft.
- `POST /api/hr-requests/{id}/submit` moves a valid draft to department approval.
- `POST /api/hr-requests/{id}/approve` accepts `{comment}` and enforces the current approval node.
- `POST /api/hr-requests/{id}/reject` records a reason and moves to `REJECTED`.
- `GET /api/hr-requests` supports type, status, employee, department, date range, page, and page size.

- [ ] **Step 1: Write state transition tests**

Test every allowed transition and rejection/cancellation path, wrong role rejection, duplicate approval rejection, stale version conflict, and employee status update after approved onboarding or offboarding.

- [ ] **Step 2: Add workflow schema**

Create `hr_request`, `onboarding_detail`, `transfer_detail`, `offboarding_detail`, and `approval_record`. Add request number uniqueness, type/status indexes, employee/status indexes, and approval node uniqueness.

- [ ] **Step 3: Implement the state machine**

`RequestStateMachine` exposes `submit`, `approve`, `reject`, and `cancel` methods that accept current state, actor scope, and node. Illegal transitions throw `STATE_CONFLICT`.

- [ ] **Step 4: Implement transactional approval and employee linkage**

Department approval requires a department manager for the target employee/department. HR approval requires `HR_ADMIN` or `SYSTEM_ADMIN`. Approved onboarding creates/activates employee organization data; approved transfer appends status history and updates department/position; approved offboarding marks the employee inactive.

- [ ] **Step 5: Run tests and commit**

Run: `mvn -f server/pom.xml test -Dtest=RequestStateMachineTest,RequestServiceTest`  
Expected: transition, role, scope, transaction, and history tests pass. Commit with `git commit -am "feat: add HR request approval workflow"`.

## Task 6: Archive Access, Return, Inventory, And Exceptions

**Files:**
- Create: `server/src/main/resources/db/migration/V5__access_inventory_schema.sql`
- Create: `server/src/main/java/com/hrplatform/access/ArchiveAccessController.java`
- Create: `server/src/main/java/com/hrplatform/access/ArchiveAccessService.java`
- Create: `server/src/main/java/com/hrplatform/inventory/InventoryController.java`
- Create: `server/src/main/java/com/hrplatform/inventory/InventoryService.java`
- Create: `server/src/main/java/com/hrplatform/access/AccessMapper.java`
- Create: `server/src/main/java/com/hrplatform/inventory/InventoryMapper.java`
- Create: `server/src/test/java/com/hrplatform/access/ArchiveAccessStateMachineTest.java`
- Create: `server/src/test/java/com/hrplatform/access/ArchiveAccessServiceTest.java`
- Create: `server/src/test/java/com/hrplatform/inventory/InventoryServiceTest.java`

**Interfaces:**
- `POST /api/archive-access` creates a draft with purpose, use type, time range, and version IDs.
- `POST /api/archive-access/{id}/submit`, `/approve`, `/use`, and `/return` perform fixed state changes.
- `GET /api/archive-access` supports applicant, status, department, due date, and pagination filters.
- `POST /api/inventory-tasks` creates a task; `POST /api/inventory-tasks/{id}/items/{itemId}/resolve` records actual status and exception resolution.

- [ ] **Step 1: Write access and inventory failure tests**

Cover unauthorized version selection, wrong department approval, duplicate use, overdue return, normal return, abnormal return, missing exception resolution, and inventory completion blocked by unresolved differences.

- [ ] **Step 2: Add access and inventory schema**

Create `archive_access_application`, `archive_access_item`, `archive_use_record`, `inventory_task`, `inventory_item`, and `exception_record` with status and due-date indexes.

- [ ] **Step 3: Implement access state machine and service**

Validate every selected version through the archive scope service before saving an item. Approval changes state and writes approval history. Use creates one active use record; return requires an active use record and changes the archive status to `RETURNED` or `ABNORMAL`.

- [ ] **Step 4: Implement inventory and exception resolution**

Inventory items snapshot employee, department, and expected version. Differences create exception records. A task can enter `COMPLETED` only when every item is resolved; unresolved differences force `ABNORMAL`.

- [ ] **Step 5: Run tests and commit**

Run: `mvn -f server/pom.xml test -Dtest=ArchiveAccessStateMachineTest,ArchiveAccessServiceTest,InventoryServiceTest`  
Expected: scope, state, exception, and transaction tests pass. Commit with `git commit -am "feat: add archive access and inventory closure"`.

## Task 7: Statistics, Audit Queries, Seed Data, And Operational Scripts

**Files:**
- Create: `server/src/main/java/com/hrplatform/statistics/StatisticsController.java`
- Create: `server/src/main/java/com/hrplatform/statistics/StatisticsService.java`
- Create: `server/src/main/java/com/hrplatform/audit/AuditController.java`
- Create: `server/src/main/java/com/hrplatform/audit/AuditMapper.java`
- Create: `server/src/main/resources/db/migration/V6__demo_seed.sql`
- Create: `scripts/verify-platform.sh`
- Create: `scripts/reset-demo-data.sh`
- Create: `docs/开发验收记录-重构版.md`

**Interfaces:**
- `GET /api/statistics/overview` accepts optional `departmentId`, `from`, and `to`, and returns employee count, archive completeness, workflow counts, access counts, OCR metrics, and average OCR duration.
- `GET /api/operation-logs` supports actor, action, object type, result, date range, and pagination.

- [ ] **Step 1: Write statistics scope tests**

Assert that employee scope, department scope, and all scope return different aggregate counts and never leak another department's data. Assert zero denominators return `0.0`.

- [ ] **Step 2: Implement aggregate SQL and audit pagination**

Use MyBatis aggregate queries with the same `DataScope` predicates as list endpoints. Return a stable DTO with nullable-safe numeric values and a generated timestamp.

- [ ] **Step 3: Add deterministic demo seed data**

Seed two departments, four roles, one user per role, three employees, three archive types, and sample requests without real personal information. Prefix all demo records with `DEMO-`.

- [ ] **Step 4: Implement verification scripts**

`verify-platform.sh` waits for health endpoints, logs in as each role, creates a demo employee, uploads an image, triggers mock OCR, checks field review, checks one approved request, checks abnormal return, checks statistics, and verifies a cross-department `403`. `reset-demo-data.sh` deletes only `DEMO-` records after an explicit `--confirm` argument.

- [ ] **Step 5: Run tests and commit**

Run: `mvn -f server/pom.xml test` and `bash scripts/verify-platform.sh` with `OCR_ENGINE=mock` in a local integration environment. Expected: all unit/integration tests pass and the script prints `platform verification complete`. Commit with `git commit -am "feat: add statistics audit and demo verification"`.

## Task 8: Vue Management Console

**Files:**
- Create: `web/src/api/http.ts`
- Create: `web/src/stores/auth.ts`
- Create: `web/src/router/index.ts`
- Create: `web/src/layouts/AppLayout.vue`
- Create: `web/src/views/LoginView.vue`
- Create: `web/src/views/DashboardView.vue`
- Create: `web/src/views/EmployeeListView.vue`
- Create: `web/src/views/EmployeeArchiveView.vue`
- Create: `web/src/views/OcrReviewView.vue`
- Create: `web/src/views/WorkflowView.vue`
- Create: `web/src/views/ArchiveAccessView.vue`
- Create: `web/src/views/InventoryView.vue`
- Create: `web/src/views/AuditLogView.vue`
- Create: `web/src/test/setup.ts`
- Create: `web/src/views/*.test.ts`

**Interfaces:**
- HTTP client attaches the bearer token, parses `ApiResponse`, clears auth on 401, and exposes structured errors for 403/validation/state conflict/OCR failure.
- Routes are `/login`, `/dashboard`, `/employees`, `/employees/:id/archive`, `/ocr/:bindingId`, `/workflows`, `/archive-access`, `/inventory`, and `/audit-logs`.

- [ ] **Step 1: Write failing component tests**

Test login success/failure, protected route redirect, employee pagination, archive upload validation, OCR low-confidence marker, field revision request, workflow approval button visibility, abnormal return display, and empty/error states.

- [ ] **Step 2: Implement API types, auth store, and router guard**

Define TypeScript types matching server DTOs. Persist only the access token and minimal user display data in session storage. Route guards use the server-provided permission list.

- [ ] **Step 3: Implement layout and dashboard**

Render role-aware navigation, traceable error messages, summary metrics, and a responsive work area. Keep actions icon-plus-text where the command is not obvious and use stable table columns.

- [ ] **Step 4: Implement employee, archive, and OCR pages**

Support filters, pagination, upload, version timeline, controlled preview/download, OCR text blocks, confidence badges, validation messages, revision history, and preview-missing state.

- [ ] **Step 5: Implement workflow, access, inventory, and audit pages**

Render forms, state timelines, approval/reject dialogs, use/return forms, abnormal resolution, inventory differences, statistics filters, and audit pagination.

- [ ] **Step 6: Run frontend tests and build**

Run:

```bash
cd web
npm test -- --run
npm run build
```

Expected: all component tests pass and Vite produces a production build. Commit with `git commit -am "feat: add Vue management console"`.

## Task 9: Final Documentation, GitHub Preparation, And Completion Audit

**Files:**
- Modify: `README.md`
- Create: `docs/api-contract.md`
- Create: `docs/database-design.md`
- Create: `docs/demo-runbook.md`
- Create: `.github/workflows/ci.yml`
- Modify: `docs/开发验收记录-重构版.md`

- [ ] **Step 1: Document local startup and environment variables**

Document MySQL/Redis/MinIO startup, OCR startup, server startup, web startup, mock OCR mode, demo credentials, and reset commands. Never commit real secrets.

- [ ] **Step 2: Document API, database relationships, and demo sequence**

Include role matrix, state diagrams, request examples, OCR failure behavior, data retention rules, and the exact end-to-end demonstration order.

- [ ] **Step 3: Add CI checks**

CI must run `mvn -f server/pom.xml test`, `npm ci && npm test -- --run && npm run build`, and `.venv/bin/pytest -q` inside `ocr-service` without requiring a real Paddle model by setting `OCR_ENGINE=mock` for Python tests that need it.

- [ ] **Step 4: Run the full verification matrix**

Run:

```bash
mvn -f server/pom.xml test
cd web && npm test -- --run && npm run build
cd ../ocr-service && .venv/bin/pytest -q
cd .. && bash scripts/verify-platform.sh
```

Expected: all commands pass; the verification script covers authentication, data scope, archive versioning, OCR success/failure, approval, access/return, inventory abnormal handling, statistics, and audit.

- [ ] **Step 5: Commit the final documentation and inspect repository state**

Run `git status --short`, confirm no `.env`, database, model cache, `target`, `node_modules`, or generated binary is tracked, then commit with `git commit -am "docs: finalize rebuilt platform delivery"`.

## Plan Self-Review

- Spec coverage: Tasks 2-7 cover every P0 backend object, state machine, permission boundary, OCR contract, audit and statistics requirement; Task 8 covers every required front-end workflow; Task 9 covers operational and GitHub deliverables.
- Placeholder scan: no task uses `TODO`, `TBD`, or an undefined future action; every task names files, interfaces, commands, and expected outcomes.
- Type consistency: server endpoints use the `ApiResponse` envelope and the same route names in Tasks 2-8; OCR DTOs are consumed by `OcrService` and `OcrController`; web routes match documented server routes.
- Residual scope: contracts, import/export, and notifications remain explicitly P1 as allowed by the design specification.
