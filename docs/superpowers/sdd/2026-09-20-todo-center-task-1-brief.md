# Task 1 Brief: 固化待办接口和 Redis key 契约

## Goal

在现有平台公共缓存 key 工具中增加待办缓存 key，并把待办接口/缓存契约同步写入已审核的设计规格和数据库设计文档。

## Files

- Modify: `backend/platform-common/platform-common-core/src/main/java/com/hrplatform/common/cache/CacheKeys.java`
- Test: `backend/platform-common/platform-common-web/src/test/java/com/hrplatform/common/cache/CacheKeysTest.java`
- Modify: `docs/superpowers/specs/2026-09-20-microservice-data-contract-design.md`
- Modify: `docs/数据库设计.md`

## Required interfaces

- Add `CacheKeys.todo(long userId, String scopeKey)` returning `hr:todo:user:{userId}:{sha256(scopeKey)}`.
- Reject user IDs <= 0. Normalize blank scope to a deterministic value. Use the existing SHA-256 key-generation pattern.
- Public query contract: `GET /api/todos?limit=1..100`.
- `TodoData` fields: `items`, `total`, `pendingApprovalCount`, `dueSoonCount`, `overdueCount`, `ocrFailedCount`, `generatedAt`.
- `TodoItem` fields: `id`, `type`, `title`, `resourceId`, `status`, `priority`, `dueAt`, `createdAt`, `targetPath`.
- Allowed types: `HR_REQUEST_APPROVAL`, `ARCHIVE_ACCESS_APPROVAL`, `ARCHIVE_RETURN_DUE`, `OCR_FAILED`.
- Allowed priorities: `HIGH`, `MEDIUM`, `LOW`.
- Todo cache TTL is 30 seconds. The cache is scope-isolated and contains only reconstructible summaries.

## TDD requirements

Write tests before production code and run the focused test while it is expected to fail. The test must assert:

```java
@Test
void todoKeyIncludesUserAndScopeDigest() {
    assertThat(CacheKeys.todo(7L, "department:12"))
            .isEqualTo("hr:todo:user:7:"
                    + "419ea9053c9df9ee9ce641d2b33b3a851e68b346e777bd55f379b330ca232ec4");
}
```

Also test blank scope normalization and invalid user IDs. Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml \
  -pl platform-common/platform-common-web -am \
  -Dtest=CacheKeysTest test
```

Then run the same command without `-Dtest` for the common-web regression suite.

## Constraints

- Do not add database tables or production todo behavior in this task.
- Do not cache OCR files, full OCR JSON, detection preview PNG, or tokens.
- Keep all existing CacheKeys behavior and tests unchanged.
- The current workspace is not a Git repository; do not invent a commit SHA. Write a report with test evidence instead.

## Report

Write the detailed implementation report to:
`docs/superpowers/sdd/2026-09-20-todo-center-task-1-report.md`

The report must include files changed, RED command/output, GREEN command/output, regression command/output, self-review, and concerns. Return only status, changed-file summary, test summary, and report path.
