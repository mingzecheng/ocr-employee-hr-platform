# P1 待办中心 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 workflow-service、archive-service 和 Vue 管理端上增加统一的待办查询，覆盖人事审批、档案访问审批、待归还/即将到期和 OCR 失败，并使用 Redis 做短时缓存。

**Architecture:** 待办接口由 workflow-service 对外提供。workflow-service 直接查询自己的 workflow_db，并通过带原 JWT 的 archive-service 内部 HTTP 接口读取授权范围内的 OCR 失败摘要；不跨库 SQL。Redis 使用 cache-aside，缓存键包含用户和 DataScope，缓存不可用时回源数据库和 archive-service。前端只通过 gateway-service 的 `/api/todos` 访问。

**Tech Stack:** Java 17, Spring Boot 3.4, MyBatis, MySQL 8, Redis, Vue 3, TypeScript, Vitest, JUnit 5, MockMvc。

## Global Constraints

- 保持核心微服务边界：前端只访问 gateway-service，workflow-service 不直接连接 identity_db 或 archive_db。
- 写入、状态迁移和审计使用 MySQL primary；待办查询使用 workflow-service 的只读路由，刚发生状态变更的确认查询使用 primary。
- Redis 只保存可重建待办摘要，TTL 固定为 30 秒；不保存原图、OCR 完整 JSON、检测框 PNG 或文件二进制。
- 所有业务响应使用 `{code,message,data,traceId}`；成功码为字符串 `"0"`。
- 所有查询必须使用 JWT 生成的 `DataScope`；客户端不得传入部门 ID 作为授权依据。
- 每个任务先写失败测试，确认失败后再写最小实现；每个任务结束运行其独立测试。
- 当前工作区不是 Git 根目录，计划中的提交步骤只在存在可用 Git 仓库时执行；否则保留可复现的文件变更和测试输出，不伪造提交记录。

---

## Task 1: 固化待办接口和 Redis key 契约

**Files:**
- Modify: `backend/platform-common/platform-common-core/src/main/java/com/hrplatform/common/cache/CacheKeys.java`
- Test: `backend/platform-common/platform-common-web/src/test/java/com/hrplatform/common/cache/CacheKeysTest.java` (create)
- Modify: `docs/superpowers/specs/2026-09-20-microservice-data-contract-design.md`
- Modify: `docs/数据库设计.md`

**Interfaces:**
- Produces `CacheKeys.todo(long userId, String scopeKey)` returning `hr:todo:user:{userId}:{sha256(scopeKey)}`.
- Produces the public query contract `GET /api/todos?limit=1..100`.
- `TodoData` fields are `items`, `total`, `pendingApprovalCount`, `dueSoonCount`, `overdueCount`, `ocrFailedCount`, and `generatedAt`.
- Each `TodoItem` contains `id`, `type`, `title`, `resourceId`, `status`, `priority`, `dueAt`, `createdAt`, and `targetPath`.
- Allowed `type` values are `HR_REQUEST_APPROVAL`, `ARCHIVE_ACCESS_APPROVAL`, `ARCHIVE_RETURN_DUE`, `OCR_FAILED`; allowed `priority` values are `HIGH`, `MEDIUM`, `LOW`.

- [ ] **Step 1: Write the failing key test**

```java
@Test
void todoKeyIncludesUserAndScopeDigest() {
    assertThat(CacheKeys.todo(7L, "department:12"))
            .isEqualTo("hr:todo:user:7:"
                    + "419ea9053c9df9ee9ce641d2b33b3a851e68b346e777bd55f379b330ca232ec4");
}
```

Use the actual SHA-256 output produced by the implementation in the assertion; also test that blank scope is normalized and user IDs less than or equal to zero are rejected.

- [ ] **Step 2: Run the focused test**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml \
  -pl platform-common/platform-common-web -am \
  -Dtest=CacheKeysTest test
```

Expected: FAIL because `CacheKeys.todo` does not exist.

- [ ] **Step 3: Implement the key helper and document the contract**

Add the helper beside `archiveList` and reuse the existing SHA-256 utility pattern. Add the `/api/todos` response shape, key format, 30-second TTL, scope isolation, and the four item types to the approved spec and `docs/数据库设计.md`.

- [ ] **Step 4: Run focused and regression tests**

Run the focused command again, then:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml \
  -pl platform-common/platform-common-web -am test
```

Expected: `CacheKeysTest` and all existing common-web tests pass.

- [ ] **Step 5: Commit when repository support is available**

```bash
git add backend/platform-common/platform-common-core/src/main/java/com/hrplatform/common/cache/CacheKeys.java \
  backend/platform-common/platform-common-web/src/test/java/com/hrplatform/common/cache/CacheKeysTest.java \
  docs/superpowers/specs/2026-09-20-microservice-data-contract-design.md docs/数据库设计.md
git commit -m "feat: define todo cache contract"
```

## Task 2: Expose scope-aware OCR failure summaries from archive-service

**Files:**
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrFailureData.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrBindingMapper.java`
- Modify: `backend/archive-service/src/main/resources/mapper/OcrBindingMapper.xml`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveAuthorizationController.java`
- Modify: `backend/archive-service/src/main/resources/db/migration/V2__ocr_todo_index.sql`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/ocr/OcrFailurePersistenceTest.java` (create)
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/api/ArchiveAuthorizationControllerTest.java`

**Interfaces:**
- Internal endpoint: `GET /internal/archive/ocr-failures?limit={limit}`.
- Request authentication is the caller's JWT; the endpoint applies the same `DataScope` used by employee/archive/OCR queries.
- Response is `ApiResponse<List<OcrFailureData>>` with `bindingId`, `versionId`, `employeeId`, `taskId`, `errorMessage`, and `updatedAt`.
- Only `ocr_binding.status = 'FAILED'` rows are returned, ordered by `updated_at DESC`, limited to 100.
- Missing/invalid scope returns no cross-scope rows; unauthenticated requests return `AUTH_REQUIRED` through the common security filter.

- [ ] **Step 1: Write failing persistence and controller tests**

Add a MySQL-backed test that inserts failed bindings in two departments and asserts that a department token receives only its department's rows. Add a MockMvc test asserting the endpoint returns the envelope and delegates the scope values to the mapper.

- [ ] **Step 2: Run focused tests to verify failure**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
mvn -f backend/pom.xml -pl archive-service -am \
  -Dtest=OcrFailurePersistenceTest,ArchiveAuthorizationControllerTest test
```

Expected: FAIL because the endpoint, mapper method, DTO, and index migration are missing.

- [ ] **Step 3: Add the covering index**

Create `V2__ocr_todo_index.sql` as a single Flyway migration that adds an index supporting failed-binding ordering and the existing version-to-employee scope join. Do not modify `V1__archive_schema.sql`; Flyway versioning provides the migration-once guarantee.

- [ ] **Step 4: Implement the scope-aware mapper query**

Add a mapper method with this signature:

```java
List<OcrFailureData> findFailedBindings(
    @Param("limit") int limit,
    @Param("scopeType") String scopeType,
    @Param("employeeId") Long employeeId,
    @Param("departmentId") Long departmentId);
```

Join `ocr_binding -> archive_version -> archive_document -> archive_record -> employee`; filter employee/archive/document status to `ACTIVE`, filter `FAILED`, apply `ALL`, `DEPARTMENT`, `EMPLOYEE`, or empty scope rules, then order by `b.updated_at DESC, b.id DESC`.

- [ ] **Step 5: Add the internal controller**

Use `@GetMapping("/internal/archive/ocr-failures")` with `@PreAuthorize("hasAuthority('PERM_ARCHIVE_READ')")`, validate `limit` to `1..100`, derive `DataScope` from `JwtPrincipal`, call the mapper, and return `ApiResponse.success(..., TraceId.current())`. Do not expose object keys or OCR file paths.

- [ ] **Step 6: Run archive tests**

Run the focused command again, then:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
mvn -f backend/pom.xml -pl archive-service -am test
```

Expected: all archive tests pass and Flyway applies V2 on a clean test database.

## Task 3: Implement workflow todo aggregation and cache-aside

**Files:**
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoController.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoDtos.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoMapper.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoService.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoExceptionHandler.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoArchiveUnavailableException.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/ArchiveTodoClient.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/HttpArchiveTodoClient.java`
- Create: `backend/workflow-service/src/main/resources/mapper/TodoMapper.xml`
- Modify: `backend/workflow-service/src/main/resources/application.yml`
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/todo/TodoServiceTest.java` (create)
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/todo/TodoControllerTest.java` (create)
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/todo/TodoPersistenceTest.java` (create)

**Interfaces:**
- `TodoService.list(WorkflowActor actor, int limit): TodoDtos.TodoData`.
- `TodoMapper.findPendingHrRequests(scopeType, employeeId, departmentId, limit): List<TodoRow>`.
- `TodoMapper.findPendingArchiveAccess(scopeType, employeeId, departmentId, limit): List<TodoRow>`.
- `TodoMapper.findDueUses(scopeType, employeeId, departmentId, now, dueSoonUntil, limit): List<TodoRow>`.
- `ArchiveTodoClient.listFailedOcr(String bearerToken, int limit): List<TodoDtos.OcrFailureItem>`.
- `TodoRow` is an internal record with `id`, `employeeId`, `status`, `dueAt`, `createdAt`, and `resourceLabel`; it is mapped to `TodoItem` only inside `TodoService`.
- `GET /api/todos?limit=50` requires `PERM_HR_REQUEST_READ` or `PERM_ARCHIVE_READ`; `limit` defaults to 50 and is capped at 100.

- [x] **Step 1: Write service tests for scope and item composition**

Cover these cases with Mockito:

```java
@Test
void departmentActorReceivesOnlyDepartmentApprovalsAndDueUses() { /* assert type, scope, ordering */ }

@Test
void allScopeActorReceivesHrApprovalsAndOcrFailures() { /* assert archive client token forwarding */ }

@Test
void employeeActorReceivesOwnDueUseButNotOtherDepartmentApproval() { /* assert no leakage */ }
```

Also test that a Redis hit returns the cached `TodoData` without querying the mapper or archive client; a Redis miss writes JSON with `Duration.ofSeconds(30)`; malformed cached JSON falls back to the data sources.

- [x] **Step 2: Run todo tests to verify failure**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
mvn -f backend/pom.xml -pl workflow-service -am \
  -Dtest=TodoServiceTest,TodoControllerTest,TodoPersistenceTest test
```

Expected: FAIL because the todo package and mapper do not exist.

- [x] **Step 3: Define DTOs and row mapping**

Use records for API DTOs. Keep database rows internal to the todo package. Map statuses as follows:

| Source row | Type | Priority | Target path |
|---|---|---|---|
| `hr_request.PENDING_DEPT_APPROVAL` | `HR_REQUEST_APPROVAL` | `HIGH` | `/app/employees/{employeeId}` |
| `hr_request.PENDING_HR_APPROVAL` | `HR_REQUEST_APPROVAL` | `HIGH` | `/app/employees/{employeeId}` |
| `archive_access_application.PENDING_DEPT_APPROVAL` or `PENDING_HR_APPROVAL` | `ARCHIVE_ACCESS_APPROVAL` | `HIGH` | `/app/employees/{employeeId}` |
| `archive_use_record.IN_USE` with due date before now | `ARCHIVE_RETURN_DUE` | `HIGH` | `/app/employees/{employeeId}` |
| `archive_use_record.IN_USE` due within 3 days | `ARCHIVE_RETURN_DUE` | `MEDIUM` | `/app/employees/{employeeId}` |
| archive internal OCR failure | `OCR_FAILED` | `MEDIUM` | `/app/employees/{employeeId}` |

Sort by priority (`HIGH`, `MEDIUM`, `LOW`), then due date ascending with nulls last, then created time descending; truncate the merged list to the requested limit and calculate counts from the returned list.

- [x] **Step 4: Implement scope-aware SQL**

Use existing tables and indexes; do not add workflow tables. Pending approvals use `target_department_id` for department scope, `status` and `current_node` for node selection, and `employee_id` for employee scope. Due-use queries join `archive_access_application` and filter by employee/department scope. Every query must include active workflow rows and deterministic ordering.

- [x] **Step 5: Implement the archive HTTP client**

Build a `RestClient` from `workflow.archive-service-url`. Add `workflow.todo.archive-connect-timeout` defaulting to `2s` and `workflow.todo.archive-read-timeout` defaulting to `5s` in `application.yml` and the test profile, and apply both to the client. Forward `Authorization: Bearer {actor.bearerToken()}`; map 401/403/404 to `WorkflowDataScopeDeniedException`, map connection/read failures to `TodoArchiveUnavailableException`, and cap the downstream `limit` at 100. The todo endpoint must not send the request to the public gateway route.

- [x] **Step 6: Implement cache-aside service**

Use `CacheKeys.todo(actor.userId(), actor.scope().cacheKey())`. Serialize `TodoData` with the existing Jackson `ObjectMapper`; use `Duration.ofSeconds(30)`. On Redis get/set failure, log at debug/warn and continue with live data. Do not cache exceptions or partial downstream responses. Keep the cache value free of tokens, object keys, raw OCR values, and file paths.

- [x] **Step 7: Add controller, error mapping, and security**

```java
@GetMapping("/api/todos")
@PreAuthorize("hasAnyAuthority('PERM_HR_REQUEST_READ','PERM_ARCHIVE_READ')")
public ApiResponse<TodoDtos.TodoData> list(
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
        Authentication authentication) {
    return ApiResponse.success(service.list(WorkflowActor.from(authentication), limit), TraceId.current());
}
```

Map archive unavailability to HTTP `502` and code `TODO_SOURCE_UNAVAILABLE`; map malformed input to `VALIDATION_ERROR`. The endpoint must return an empty successful list for a valid actor with no open items.

- [x] **Step 8: Run workflow tests and regression suite**

Run focused todo tests, then:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/workflow_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/workflow_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
mvn -f backend/pom.xml -pl workflow-service -am test
```

Expected: existing workflow state, persistence, authorization, and data-scope tests remain green.

## Task 4: Route the API through gateway and add frontend todo surface

**Files:**
- Modify: `backend/gateway-service/src/main/resources/application.yml`
- Modify: `backend/gateway-service/src/test/java/com/hrplatform/gateway/GatewayRouteConfigurationTest.java`
- Create: `frontend/src/api/todos.ts`
- Modify: `frontend/src/types/api.ts`
- Create: `frontend/src/components/TodoSummary.vue`
- Create: `frontend/src/views/TodoView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/layouts/AppLayout.vue`
- Modify: `frontend/src/views/DashboardView.vue`
- Modify: `frontend/src/styles/index.css`
- Test: `frontend/src/components/TodoSummary.test.ts` (create)
- Test: `frontend/src/views/TodoView.test.ts` (create)
- Modify: `frontend/src/views/DashboardView.test.ts`

**Interfaces:**
- Gateway route `/api/todos/**` forwards to `${WORKFLOW_SERVICE_URL}`.
- `frontend/src/api/todos.ts` exports `getTodos(limit = 50): Promise<TodoData>` and uses `unwrap`.
- Dashboard renders a compact summary and `/app/todos` renders the full list; clicking an item uses its `targetPath` through the router.

- [x] **Step 1: Write frontend tests first**

Cover loading, empty state, four item labels, priority styling, API failure state, and dashboard summary count. Mock `getTodos` exactly as existing dashboard/archive tests mock API modules; do not make tests depend on a running backend.

- [x] **Step 2: Run focused frontend tests to verify failure**

```bash
cd frontend
npm test -- --run src/components/TodoSummary.test.ts src/views/TodoView.test.ts src/views/DashboardView.test.ts
```

Expected: FAIL because the API module, route, components, and `TodoData` types do not exist.

- [x] **Step 3: Add type and API definitions**

Add `TodoType`, `TodoPriority`, `TodoItem`, and `TodoData` to `frontend/src/types/api.ts`. Implement `getTodos` with `http.get('/todos', { params: { limit } })` and `unwrap(response)`.

- [x] **Step 4: Add route and gateway route**

Add the gateway route before any broader workflow route if necessary, and add the named Vue route `todos` under `/app`. The route guard remains the existing auth guard; no client-side role claims are trusted for data filtering.

- [x] **Step 5: Implement the todo UI**

`TodoSummary.vue` displays total and category counts with stable rows. `TodoView.vue` displays loading, error, empty, and populated states, with priority/status indicators and keyboard-accessible links. Keep the current unframed workbench style, use existing Element Plus icons, avoid nested cards, and ensure the list remains usable at the existing 760px mobile breakpoint.

- [x] **Step 6: Integrate the dashboard and navigation**

Load todos independently from employee/statistics requests so a todo failure does not hide existing dashboard data. Add a navigation item with a count only when the API succeeds. Preserve logout and 401 handling through the existing auth store/interceptor.

- [x] **Step 7: Run frontend verification**

```bash
cd frontend
npm test -- --run
npm run build
```

Expected: all existing and new tests pass, `vue-tsc` reports no errors, and the production build succeeds.

## Task 5: End-to-end verification and documentation

**Files:**
- Modify: `scripts/verify-platform.sh`
- Modify: `docs/开发验收记录.md`
- Modify: `docs/方案设计.md`
- Modify: `docs/数据库设计.md`

**Interfaces:**
- Verification creates one pending approval, one due use, and one failed OCR binding in an isolated local database, then queries `/api/todos` with administrator and department-scoped tokens.
- It asserts the response envelope, item types, count fields, scope isolation, and Redis key TTL between 1 and 30 seconds after a cache hit.
- It asserts that stopping Redis does not make the endpoint fail if the workflow and archive services remain available; the result is served from live data and logs the cache fallback.

- [ ] **Step 1: Add the gateway smoke assertions**

Use `curl` and `jq` already used by `scripts/verify-platform.sh`. Do not inspect or expose OCR files directly; create the failed OCR state through the existing archive/OCR path or use a dedicated test fixture in the local database setup.

- [ ] **Step 2: Run all service and frontend tests**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml test
cd ocr-service && .venv/bin/python -m pytest -q
cd ../frontend && npm test -- --run && npm run build
```

Expected: all current tests and the new todo tests pass.

- [ ] **Step 3: Run the local platform verifier**

```bash
bash scripts/verify-platform.sh
```

Expected: infrastructure health, login, scope checks, existing OCR/preview checks, workflow checks, and the new todo assertions all pass.

- [ ] **Step 4: Record evidence and mark the plan task complete**

Append the exact test counts, service ports, Redis TTL observation, scope assertions, and any known limitation to `docs/开发验收记录.md`. Mark only verified plan checkboxes as `[x]`; leave contract extensions not implemented as explicit P1 backlog items.

## Plan self-review

- **Spec coverage:** service boundary, MySQL routing, Redis cache-aside, scope filtering, internal archive authorization, OCR failure evidence, API envelope, frontend gateway-only access, tests, and local verification are covered by Tasks 1-5.
- **No placeholders:** this plan contains no unfinished markers, vague handling instructions, or undefined neighboring interface. The future contract uses concrete DTO fields, paths, key formats, SQL filters, limits, TTLs, and commands.
- **Type consistency:** `TodoService.list(WorkflowActor,int)`, `TodoData`, `TodoItem`, `ArchiveTodoClient.listFailedOcr`, `CacheKeys.todo`, and `GET /api/todos` are used consistently across backend, gateway, frontend, and tests.
- **Scope boundary:** contracts, P1 todo cache, and its end-to-end verification are one independently testable sub-project. Contract management and batch import/export remain separate plans and are not mixed into this implementation.
