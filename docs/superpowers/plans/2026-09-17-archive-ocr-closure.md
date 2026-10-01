# 档案 OCR 闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 `archive-service` 的员工、档案材料、文件版本和 OCR 证据闭环，使图片上传、文字识别、检测框预览、字段确认和版本追溯可以通过网关访问。

**Architecture:** `archive-service` 独占 `archive_db`，写入主库、只读查询副本；文件保存通过可替换的 `ObjectStorage` 接口完成，生产配置使用 MinIO，测试使用内存实现。档案服务以 HTTP 调用现有 Python OCR 服务，业务库只保存 `taskId`、OCR 状态、引擎版本、原图哈希、预览元数据和字段确认，不复制 OCR 文本块坐标的事实数据。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring MVC, MyBatis, Flyway, MySQL 8, MinIO S3-compatible storage, JUnit 5, MockMvc.

## Global Constraints

- 服务之间只使用 HTTP API 和业务 ID；禁止 archive-service 读取 OCR SQLite 或 identity 数据库。
- 所有写入使用主库，`@Transactional(readOnly = true)` 查询使用副本，写入后确认读取使用主库。
- OCR 原始任务结果和检测框文件继续由 `ocr-service` 保留；档案库只保存绑定与追溯元数据。
- 文件扩展名、MIME、实际图片内容和 SHA-256 必须在创建 OCR 任务前校验。
- 检测框不存在返回 `DETECTION_PREVIEW_NOT_FOUND`，不能把已成功的 OCR 任务改成失败。
- 所有 HTTP 响应使用公共 `ApiResponse`，不把异常堆栈或下游内部路径返回给前端。
- 每个行为先写失败测试，再写最小实现；已有 OCR Python 测试必须持续通过。

---

### Task 1: 修复基础事务和 traceId 回归

**Files:**
- Modify: `backend/identity-service/src/main/java/com/hrplatform/identity/config/IdentitySeedInitializer.java`
- Modify: `backend/gateway-service/src/main/java/com/hrplatform/gateway/TraceIdGlobalFilter.java`
- Test: `backend/identity-service/src/test/java/com/hrplatform/identity/config/IdentitySeedInitializerTest.java`
- Test: `backend/gateway-service/src/test/java/com/hrplatform/gateway/TraceIdGlobalFilterTest.java`

**Interfaces:**
- `IdentitySeedInitializer.run(...)` 是事务边界，初始化过程内的查询、用户插入和角色绑定必须在同一事务中执行。
- 网关响应只保留一个 `X-Trace-Id`，其值与转发请求中的 traceId 相同。

- [ ] **Step 1: Write failing tests** for transaction annotation on `run` and duplicate response header behavior.
- [ ] **Step 2: Run the focused tests** and confirm the current implementation fails for the expected reasons.
- [ ] **Step 3: Move `@Transactional` to `run` and set the response header after removing existing values.**
- [ ] **Step 4: Run the focused tests and the existing gateway/identity tests.**

### Task 2: Establish archive-service dependencies and data source

**Files:**
- Modify: `backend/archive-service/pom.xml`
- Modify: `backend/archive-service/src/main/resources/application.yml`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/config/ArchiveDataSourceConfiguration.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/ArchiveApplicationTest.java`

**Interfaces:**
- The service exposes `/actuator/health` and binds `app.datasource.write/read` exactly like identity-service.
- `ArchiveDataSourceConfiguration` provides the primary routing `DataSource`, `DataSourceTransactionManager`, MyBatis mapper scan, and Flyway-compatible JDBC data source.
- Configurable properties include `OCR_SERVICE_URL`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, and `ARCHIVE_STORAGE_PREFIX`.

- [ ] **Step 1: Add a context test** that loads the archive application with test datasource and storage properties.
- [ ] **Step 2: Run it and verify the missing configuration/dependencies fail clearly.**
- [ ] **Step 3: Add Web, Validation, MyBatis, MySQL, Flyway, MinIO, and common module dependencies plus routing configuration.**
- [ ] **Step 4: Run the archive context test and verify the health endpoint context starts.**

### Task 3: Add archive schema and persistence contracts

**Files:**
- Create: `backend/archive-service/src/main/resources/db/migration/V1__archive_schema.sql`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/employee/Employee.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveDocument.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveVersion.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrBinding.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrFieldConfirmation.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/employee/EmployeeMapper.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveMapper.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrBindingMapper.java`
- Create: `backend/archive-service/src/main/resources/mapper/EmployeeMapper.xml`
- Create: `backend/archive-service/src/main/resources/mapper/ArchiveMapper.xml`
- Create: `backend/archive-service/src/main/resources/mapper/OcrBindingMapper.xml`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/ArchiveSchemaTest.java`

**Interfaces:**
- Tables are `employee`, `archive_record`, `archive_document`, `file_object`, `archive_version`, `ocr_binding`, `ocr_field_confirmation`, and `audit_log`; no cross-database foreign keys are used.
- `EmployeeMapper.insert(Employee)`, `findById(long)`, and `list(int,int)` provide employee persistence.
- `ArchiveMapper.insertDocument`, `insertVersion`, `findVersion`, and `findDocument` persist immutable version records and their file metadata.
- `OcrBindingMapper.insert`, `findByVersionId`, `findById`, and `insertConfirmation` persist the OCR association and field confirmation history.

- [ ] **Step 1: Write mapper/schema tests** asserting unique employee number, version number uniqueness per document, SHA-256 storage, OCR task uniqueness per version, and confirmation history append-only behavior.
- [ ] **Step 2: Run them against the configured Compose MySQL primary and confirm they fail before the migration/mappers exist.**
- [ ] **Step 3: Add the migration, Java records/POJOs, explicit SQL mappers, and MyBatis scan.**
- [ ] **Step 4: Run the focused persistence tests against primary and read replica, verifying writes do not target the replica.**

### Task 4: Implement storage and OCR HTTP adapters

**Files:**
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/storage/ObjectStorage.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/storage/MinioObjectStorage.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrClient.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/HttpOcrClient.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrTaskData.java`
- Create: `backend/archive-service/src/test/java/com/hrplatform/archive/storage/MinioObjectStorageTest.java`
- Create: `backend/archive-service/src/test/java/com/hrplatform/archive/ocr/HttpOcrClientTest.java`

**Interfaces:**
- `ObjectStorage.put(String key, String contentType, byte[] content)` returns key, size, SHA-256; `ObjectStorage.get(String key)` returns a byte stream and content type.
- `OcrClient.createTask(String filename, String contentType, byte[] content, String documentType)` calls `POST /api/ocr/tasks` multipart and returns the decoded `data` object without changing OCR field names or bbox coordinates.
- `OcrClient.getDetectionPreview(String taskId)` returns image bytes or maps downstream 404 code `DETECTION_PREVIEW_NOT_FOUND` to `OcrPreviewMissingException`.

- [ ] **Step 1: Write adapter tests** for multipart field names, successful JSON envelope decoding, downstream preview bytes, and stable mapping of missing previews.
- [ ] **Step 2: Run them and verify the adapters are absent/failing.**
- [ ] **Step 3: Implement MinIO and `RestClient` adapters with bounded response size and stable downstream error mapping.**
- [ ] **Step 4: Run the focused adapter tests using mock HTTP and mocked MinIO client behavior.**

### Task 5: Implement archive OCR orchestration

**Files:**
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/employee/EmployeeService.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveService.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveController.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveDtos.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveExceptionHandler.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/api/ArchiveControllerTest.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/document/ArchiveServiceTest.java`

**Interfaces:**
- `POST /api/employees` creates an employee with an immutable employee number and returns its ID.
- `GET /api/employees` lists employees with bounded pagination.
- `POST /api/archive/employees/{employeeId}/documents` accepts PNG/JPEG multipart, validates image content and size, stores the object, creates document/version rows, and returns the version ID plus SHA-256.
- `POST /api/archive/versions/{versionId}/ocr` loads the version bytes, calls OCR with `documentType`, stores the binding and initial field confirmation values, and returns the OCR result aggregate.
- `GET /api/archive/versions/{versionId}/ocr-result` returns the binding and OCR task ID/status/engine/preview metadata.
- `GET /api/archive/ocr-bindings/{bindingId}/detection-preview` streams the OCR preview; missing preview returns `DETECTION_PREVIEW_NOT_FOUND` without changing binding status.
- `PUT /api/archive/ocr-bindings/{bindingId}/fields/{fieldCode}` calls OCR field correction using the current authenticated user and appends a confirmation record.

- [ ] **Step 1: Write MockMvc and service tests** for upload validation, version creation, OCR binding, duplicate OCR idempotency, preview proxy success/missing behavior, and field confirmation.
- [ ] **Step 2: Run tests and verify the controller/service contracts fail before implementation.**
- [ ] **Step 3: Implement validation, transactions, idempotency by `(version_id, task_id/status)`, DTO mapping, authorization hooks, and stable error responses.**
- [ ] **Step 4: Run the focused archive tests and verify the complete mocked upload-to-preview flow.**

### Task 6: Wire gateway and run environment verification

**Files:**
- Modify: `backend/gateway-service/src/main/resources/application.yml`
- Modify: `backend/gateway-service/src/main/resources/application-test.yml`
- Modify: `infra/mysql/primary/init/01-create-databases.sql`
- Modify: `scripts/verify-platform.sh`
- Modify: `backend/README.md`

**Interfaces:**
- Gateway routes `/api/employees/**` and `/api/archive/**` to archive-service without exposing OCR SQLite or internal storage paths.
- Compose creates `archive_db` and grants the application user only the required archive database privileges.
- Verification script checks archive health, employee creation, image upload, OCR binding, OCR result retrieval, preview proxy, and the `DETECTION_PREVIEW_NOT_FOUND` contract.

- [ ] **Step 1: Add route/configuration assertions and extend the verification script with the archive OCR flow.**
- [ ] **Step 2: Run route tests and verify the new route checks fail before wiring.**
- [ ] **Step 3: Add route and database initialization changes, then update README environment variables and curl examples.**
- [ ] **Step 4: Run Java tests, OCR Python tests, Compose verification, and direct gateway smoke tests.**

## Verification Gate

Run from the project root with the Compose ports from the existing verification environment:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_USERNAME=hr MYSQL_PASSWORD='change-me-application-password' \
REDIS_HOST=127.0.0.1 REDIS_PORT=16379 \
mvn -f backend/pom.xml clean test
```

```bash
cd ocr-service && python3 -m pytest -q
```

The phase is complete only when both commands exit with code 0 and the archive gateway smoke flow returns a persisted OCR task, a non-empty detection preview, or the explicit `DETECTION_PREVIEW_NOT_FOUND` response for a deliberately missing preview.
