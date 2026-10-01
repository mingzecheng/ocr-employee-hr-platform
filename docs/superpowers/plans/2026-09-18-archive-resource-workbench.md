# 档案资源工作台实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 archive-service 和 Vue 管理端补齐 scope-aware 的档案材料查询、版本历史和原图受控下载能力。

**Architecture:** 复用 `archive_db` 的 `archive_document`、`archive_version` 和 `file_object` 表，通过 MyBatis join 员工归属并应用 `DataScope`。Controller 只负责 HTTP 响应，Service 在读取 MinIO 前完成授权；Vue 通过 Gateway 获取 JSON 和 blob 文件。

**Tech Stack:** Java 17, Spring Boot, MyBatis, MySQL read/write routing, MinIO, Redis existing cache layer, Vue 3, TypeScript, Axios, Element Plus, Vitest.

## Global Constraints

- 不新增跨服务数据库查询、跨库外键或客户端可控的 object key。
- 所有材料、版本和下载查询必须携带 `DataScope`；越权统一返回 `403/FORBIDDEN`。
- 文件内容不进入 Redis；材料 JSON 只在 scope 允许后返回。
- 修改生产代码前先写一个能正确失败的测试；测试必须使用 Java 17。
- 保持现有 OCR、员工列表、统计和 workflow 接口兼容。

---

### Task 1: Backend resource contracts and failing tests

**Files:**
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveDtos.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveMapper.java`
- Modify: `backend/archive-service/src/test/java/com/hrplatform/archive/document/ArchiveServiceTest.java`

**Interfaces:**
- Add `ArchiveDtos.DocumentData`, `ArchiveDtos.VersionData`, and `ArchiveDtos.DownloadData` records.
- Add mapper signatures for document and version lists with `scopeType`, `employeeId`, and `departmentId` parameters.
- Service tests will describe the public behavior before mapper XML and service implementation exist.

- [x] **Step 1: Write failing service tests**

Add tests proving:

```java
@Test
void listsDocumentsOnlyThroughTheCurrentScope() {
    DataScope scope = DataScope.department(12L, 9L);
    when(archiveMapper.listDocumentsByEmployeeInScope(7L, "DEPARTMENT", null, 12L))
            .thenReturn(List.of(documentData(21L, 7L)));

    assertThat(service.listDocuments(7L, scope)).hasSize(1);
    verify(archiveMapper).listDocumentsByEmployeeInScope(7L, "DEPARTMENT", null, 12L);
}

@Test
void downloadRefusesOutOfScopeVersionBeforeObjectStorage() {
    when(archiveMapper.findVersionByIdInScope(31L, "EMPLOYEE", 7L, null)).thenReturn(null);

    assertThatThrownBy(() -> service.downloadVersion(31L, DataScope.employee(7L, 9L)))
            .isInstanceOf(DataScopeDeniedException.class);
    verifyNoInteractions(objectStorage);
}
```

- [x] **Step 2: Run the focused tests and confirm the expected compile failure**

Run from `backend` with Java 17:

```bash
JAVA_HOME=/Users/mingzechen/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home \
PATH=/Users/mingzechen/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home/bin:$PATH \
mvn -pl archive-service -am -Dtest=ArchiveServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected failure: the new DTOs and service methods do not exist yet.

### Task 2: Scope-aware MyBatis queries and service implementation

**Files:**
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveDtos.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveMapper.java`
- Modify: `backend/archive-service/src/main/resources/mapper/ArchiveMapper.xml`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveService.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveExceptionHandler.java`

**Interfaces:**
- `listDocumentsByEmployeeInScope(long employeeId, String scopeType, Long scopeEmployeeId, Long departmentId)` returns document summaries joined through the employee.
- `listVersionsByDocumentInScope(long documentId, String scopeType, Long scopeEmployeeId, Long departmentId)` returns version/file metadata joined through the employee.
- `listDocuments`, `listVersions`, and `downloadVersion` reject null/empty results as `DataScopeDeniedException` for an out-of-scope resource.
- `downloadVersion` returns `DownloadData(fileName, contentType, content)` and invokes `ObjectStorage.get` only after `findVersionByIdInScope` succeeds.

- [x] **Step 1: Add minimal DTOs and mapper signatures so the failing tests compile.**
- [x] **Step 2: Add scope predicates to both SQL queries and map summary fields.**
- [x] **Step 3: Implement service methods and safe download metadata.**
- [x] **Step 4: Add `STORAGE_ERROR` exception mapping without exposing storage internals.**
- [x] **Step 5: Run `ArchiveServiceTest`, archive persistence tests, and the full archive module.**

### Task 3: Controller endpoints and HTTP response tests

**Files:**
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveController.java`
- Modify: `backend/archive-service/src/test/java/com/hrplatform/archive/api/ArchiveControllerTest.java`

**Interfaces:**
- `GET /api/archive/employees/{employeeId}/documents` returns `ApiResponse<List<DocumentData>>`.
- `GET /api/archive/documents/{documentId}/versions` returns `ApiResponse<List<VersionData>>`.
- `GET /api/archive/versions/{versionId}/download` returns `ResponseEntity<byte[]>` with `Content-Type` and UTF-8 `Content-Disposition`.

- [x] **Step 1: Add failing MockMvc tests for success and scope denial.**
- [x] **Step 2: Implement controller methods using the authenticated `DataScope`.**
- [x] **Step 3: Verify controller tests and archive security tests.**

### Task 4: Vue materials and version workbench

**Files:**
- Modify: `frontend/src/api/archive.ts`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/views/EmployeeArchiveView.vue`
- Modify: `frontend/src/styles/index.css`
- Test: `frontend/src/views/EmployeeArchiveView.test.ts`

**Interfaces:**
- `listArchiveDocuments(employeeId)` calls the protected JSON endpoint.
- `listArchiveVersions(documentId)` calls the protected JSON endpoint.
- `getVersionFile(versionId)` returns a blob and is used only for preview/download.

- [x] **Step 1: Add failing tests for material empty state, version display, and protected blob loading.**
- [x] **Step 2: Implement API types and methods.**
- [x] **Step 3: Add materials and versions UI while preserving the existing OCR panel.**
- [x] **Step 4: Revoke blob URLs on replacement and component unmount.**
- [x] **Step 5: Run Vitest, TypeScript build, and existing frontend tests.**

### Task 5: End-to-end verification and documentation

**Files:**
- Modify: `scripts/verify-platform.sh`
- Modify: `docs/开发验收记录.md`
- Modify: `docs/数据库设计.md`
- Modify: `docs/需求理解.md`

- [x] **Step 1: Add gateway checks for document list, version list, and file download bytes/content type.**
- [x] **Step 2: Add a cross-scope download assertion returning `403/FORBIDDEN`.**
- [x] **Step 3: Run Java 17 backend tests, OCR tests, frontend tests/build, and the full verification script.**
- [x] **Step 4: Record the completed feature and leave workflow scope snapshot as an explicit boundary.**

## Verification Gate

The stage is complete only when all of the following are fresh and successful:

```bash
JAVA_HOME=/Users/mingzechen/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home \
PATH=/Users/mingzechen/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home/bin:$PATH \
MYSQL_USERNAME=hr MYSQL_PASSWORD='change-me-application-password' \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?...' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?...' \
mvn -pl archive-service -am test
```

```bash
cd ocr-service && .venv/bin/python -m pytest -q
cd frontend && npm test -- --run && npm run build
```

The gateway verifier must prove that an authorized user can query and download a file and that a user from another scope cannot download the same version.
