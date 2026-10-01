# Task 3 Brief: Aggregate workflow todos with cache-aside

## Goal

Add the workflow-service todo query. It must merge workflow-owned pending HR approvals, archive access approvals, archive return due items, and the archive-service OCR failure summaries into one scope-filtered response. The public endpoint is `GET /api/todos`; workflow-service remains the only owner of this public contract and must not query archive_db or identity_db.

## Files

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
- Modify: `backend/workflow-service/src/main/resources/application-test.yml`
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/todo/TodoServiceTest.java`
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/todo/TodoControllerTest.java`
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/todo/TodoPersistenceTest.java`

## Contract

- `TodoService.list(WorkflowActor actor, int limit): TodoDtos.TodoData`.
- `TodoMapper.findPendingHrRequests(scopeType, employeeId, departmentId, limit): List<TodoRow>`.
- `TodoMapper.findPendingArchiveAccess(scopeType, employeeId, departmentId, limit): List<TodoRow>`.
- `TodoMapper.findDueUses(scopeType, employeeId, departmentId, now, dueSoonUntil, limit): List<TodoRow>`.
- `ArchiveTodoClient.listFailedOcr(String bearerToken, int limit): List<TodoDtos.OcrFailureItem>`.
- `GET /api/todos?limit=50` requires `PERM_HR_REQUEST_READ` or `PERM_ARCHIVE_READ`; default 50, cap 100.
- All successful responses use `ApiResponse.success(..., TraceId.current())` and the existing `{code,message,data,traceId}` envelope.
- `TodoData` fields: `items`, `total`, `pendingApprovalCount`, `dueSoonCount`, `overdueCount`, `ocrFailedCount`, `generatedAt`.
- `TodoItem` fields: `id`, `type`, `title`, `resourceId`, `status`, `priority`, `dueAt`, `createdAt`, `targetPath`.
- Allowed types: `HR_REQUEST_APPROVAL`, `ARCHIVE_ACCESS_APPROVAL`, `ARCHIVE_RETURN_DUE`, `OCR_FAILED`.
- Allowed priorities: `HIGH`, `MEDIUM`, `LOW`.

## Scope and mapping rules

- Derive scope only from `WorkflowActor.scope()` generated from the authenticated JWT. Never accept department or employee scope from query parameters.
- HR requests use `target_department_id` for department scope, `employee_id` for employee scope, and pending statuses `PENDING_DEPT_APPROVAL` / `PENDING_HR_APPROVAL` with matching current nodes.
- Archive access applications use `target_department_id` / `employee_id`, pending statuses `PENDING_DEPT_APPROVAL` / `PENDING_HR_APPROVAL`, and active workflow rows.
- Due-use queries join `archive_access_application`, select `archive_use_record.IN_USE`, and apply employee/department scope. Rows due before `now` are overdue; rows due from `now` through `now + 3 days` are due soon.
- `DataScope.Type.NONE` must return no rows. `ALL` is unrestricted.
- Map source rows as follows:
  - HR request pending: type `HR_REQUEST_APPROVAL`, priority `HIGH`, target `/app/employees/{employeeId}`.
  - Archive access pending: type `ARCHIVE_ACCESS_APPROVAL`, priority `HIGH`, target `/app/employees/{employeeId}`.
  - In-use overdue: type `ARCHIVE_RETURN_DUE`, priority `HIGH`, target `/app/employees/{employeeId}`.
  - In-use due within 3 days: type `ARCHIVE_RETURN_DUE`, priority `MEDIUM`, target `/app/employees/{employeeId}`.
  - OCR failure: type `OCR_FAILED`, priority `MEDIUM`, target `/app/employees/{employeeId}`.
- Sort merged items by priority (`HIGH`, `MEDIUM`, `LOW`), due date ascending with nulls last, then created time descending. Truncate to requested limit. Calculate all counts from the returned list.

## Cache-aside and archive client

- Use `CacheKeys.todo(actor.userId(), actor.scope().cacheKey())`.
- Cache only the serialised `TodoData`; TTL is exactly `Duration.ofSeconds(30)`. Never cache tokens, object keys, raw OCR values, file paths, exceptions, or partial responses.
- On Redis get/set/serialization failure, log and continue with live data. A malformed cached JSON value must fall back to data sources.
- `HttpArchiveTodoClient` uses the existing `RestClient` pattern and `${workflow.archive-service-url}`. Add `workflow.todo.archive-connect-timeout` default `2s` and `workflow.todo.archive-read-timeout` default `5s` to both application profiles and apply them to the client.
- Forward `Authorization: Bearer {actor.bearerToken()}` to `/internal/archive/ocr-failures?limit={limit}`. Missing token or downstream 401/403/404 maps to `WorkflowDataScopeDeniedException`; connection/read failures map to `TodoArchiveUnavailableException`. Downstream limit is capped at 100.

## TDD and verification

Write tests first and run the focused RED command before production code:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
mvn -f backend/pom.xml -pl workflow-service -am \
  -Dtest=TodoServiceTest,TodoControllerTest,TodoPersistenceTest test
```

Service tests must cover department scope, all scope with token forwarding, employee scope, cache hit, cache miss with 30-second write, and malformed cached JSON fallback. Controller tests must cover the envelope, default/capped limit, and archive-unavailable mapping. Persistence tests must prove department/employee filtering and deterministic ordering against workflow_db. After implementation rerun the focused command, then the full workflow-service suite with the configured MySQL read/write URLs.

## Constraints

- Keep the existing workflow authorization and exception handling intact.
- Do not add workflow tables or cross-database SQL.
- Do not modify gateway, frontend, archive-service, or OCR service in this task.
- The workspace is not a Git repository; do not commit or invent a SHA.

## Report

Write the full report to:
`docs/superpowers/sdd/2026-09-20-todo-center-task-3-report.md`

Include RED/GREEN commands and outputs, regression result, files changed, self-review, and concerns. Return only status, files changed, test summary, and report path.
