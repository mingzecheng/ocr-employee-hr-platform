# Task 2 Brief: Expose scope-aware OCR failure summaries

## Goal

Add an authenticated archive-service internal endpoint that returns failed OCR binding summaries within the caller's JWT DataScope. This feeds workflow-service's todo aggregation without cross-database SQL.

## Files

- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrFailureData.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrBindingMapper.java`
- Modify: `backend/archive-service/src/main/resources/mapper/OcrBindingMapper.xml`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveAuthorizationController.java`
- Modify: `backend/archive-service/src/main/resources/db/migration/V2__ocr_todo_index.sql`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/ocr/OcrFailurePersistenceTest.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/api/ArchiveAuthorizationControllerTest.java`

## Contract

- `GET /internal/archive/ocr-failures?limit={limit}`.
- Require `PERM_ARCHIVE_READ` with `@PreAuthorize`.
- Derive `DataScope` from the authenticated `JwtPrincipal`; do not trust query parameters for scope.
- Return `ApiResponse<List<OcrFailureData>>` with `bindingId`, `versionId`, `employeeId`, `taskId`, `errorMessage`, and `updatedAt`.
- Select only `ocr_binding.status = 'FAILED'`, order by `updated_at DESC, id DESC`, and cap limit at 100.
- Join `ocr_binding -> archive_version -> archive_document -> archive_record -> employee`; require employee, archive record, and document status `ACTIVE`.
- Apply `ALL`, `DEPARTMENT`, `EMPLOYEE`, and `NONE` scope filters exactly as existing scope-aware OCR queries.
- Do not expose object keys or OCR file paths.

## TDD and verification

Write the persistence and controller tests first. Persistence test must insert failed bindings in two departments and prove a department scope sees only its department. Controller test must prove the response envelope and scope arguments. Run the focused command before implementation:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
mvn -f backend/pom.xml -pl archive-service -am \
  -Dtest=OcrFailurePersistenceTest,ArchiveAuthorizationControllerTest test
```

After implementation rerun the focused command and then the archive-service suite with the same datasource variables.

## Migration

Create `V2__ocr_todo_index.sql` without editing `V1__archive_schema.sql`. Add an index supporting `status, updated_at` ordering; Flyway versioning makes it a one-time migration.

## Constraints

- Keep archive-service's existing scope checks and exception handling intact.
- Do not add a public gateway route for this internal endpoint.
- Do not modify workflow-service, frontend, or OCR service in this task.
- The workspace is not a Git repository; do not commit or invent a SHA.

## Report

Write the full report to:
`docs/superpowers/sdd/2026-09-20-todo-center-task-2-report.md`

Include RED/GREEN commands and outputs, regression result, files changed, self-review, and concerns. Return only status, files changed, test summary, and report path.
