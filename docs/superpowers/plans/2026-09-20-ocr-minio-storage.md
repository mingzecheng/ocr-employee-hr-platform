# OCR MinIO 统一对象存储实施计划

> **For agentic workers:** 当前工作区不是 Git 仓库，不能执行 commit；每个任务以测试、构建和可审阅 diff 作为检查点。实现前后必须保留本地文件备份，不得直接删除历史 OCR 数据。

**Goal:** 将 OCR 原图和检测框效果图从本地 `data` 目录迁移到 MinIO，使档案 OCR 链路复用 archive-service 已保存的 MinIO 原图，OCR 本地只保留 SQLite 任务元数据。

**Architecture:** archive-service 继续负责首次上传档案原图并保存 `archive/...` 对象。触发 OCR 时通过带内部令牌的对象引用请求把实际 MinIO key 传给 ocr-service；ocr-service 临时下载对象完成识别，把检测框 PNG 上传到 `ocr/preview/...`，SQLite 只保存任务 JSON 和内部对象引用。独立 OCR multipart 上传仍受支持，并写入按 SHA-256 去重的 `ocr/source/...` 对象。

**Tech Stack:** Python 3.10+, FastAPI, PaddleOCR, SQLite, MinIO Python SDK; Java 17, Spring Boot 3.4.5, MyBatis, MinIO Java SDK; Pytest, JUnit 5, Bash platform verifier.

## Global Constraints

- 对外 OCR 路径和响应包络保持不变：`POST /api/ocr/tasks`、任务查询、检测框预览和字段修订接口不能改变。
- 不允许 ocr-service 直接连接 `archive_db`；档案对象引用只能由 archive-service 通过 HTTP 内部合同传递。
- 原图、检测框 PNG 和文件二进制不得进入 MySQL 或 Redis；SQLite 只保存任务/识别元数据和对象引用。
- 迁移默认只读、幂等和不删除；只有显式 `--delete-local-binaries` 且 MinIO 校验成功后才可删除本地图片。
- 内部对象引用必须校验共享令牌、bucket、`archive/` 前缀和路径安全性；日志和 API 响应不得输出 MinIO 密钥或内部共享令牌。
- 所有新行为先写失败测试，再实现最小代码；每个任务结束都运行对应的 focused test。
- 当前本地运行环境的 MinIO API 为 `http://127.0.0.1:19000`，控制台为 `http://127.0.0.1:19001`；测试命令使用环境变量覆盖默认端口。

---

### Task 1: MinIO 存储适配层与配置

**Files:**
- Create: `ocr-service/app/object_storage.py`
- Modify: `ocr-service/app/config.py`
- Modify: `ocr-service/requirements.txt`
- Modify: `ocr-service/.env.example`
- Test: `ocr-service/tests/test_object_storage.py`
- Test: `ocr-service/tests/test_startup.py`

**Interfaces:**
- Produces `ObjectStorage` protocol with `put_bytes(key, content_type, content)`, `get_bytes(key)`, `download_to_path(key, path)`, `stat(key)` and `delete(key)` methods.
- Produces `StoredObject` containing `key`, `content_type`, `size`, `sha256` and optional `content`.
- `MinioObjectStorage` receives endpoint, access key, secret key, bucket and allowed source prefix from `Settings`; it uses the configured bucket only and never accepts a caller-provided bucket.

- [ ] **Step 1: Write failing storage tests.**

  Add an in-memory fake storage used by tests and cover:

  ```python
  def test_put_bytes_is_idempotent_for_same_key_and_content():
      storage = InMemoryObjectStorage()
      first = storage.put_bytes("ocr/source/a.png", "image/png", b"png")
      second = storage.put_bytes("ocr/source/a.png", "image/png", b"png")
      assert first.sha256 == second.sha256
      assert storage.get_bytes("ocr/source/a.png").content == b"png"
  
  def test_missing_object_raises_storage_not_found():
      with pytest.raises(StorageObjectNotFound):
          InMemoryObjectStorage().get_bytes("archive/missing.png")
  ```

  Add a configuration test asserting `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, `MINIO_ALLOWED_SOURCE_PREFIX` and `OCR_INTERNAL_TOKEN` are read from the environment.

- [ ] **Step 2: Run focused tests and verify they fail for missing production interfaces.**

  Run:

  ```bash
  cd ocr-service
  .venv/bin/pytest -q tests/test_object_storage.py tests/test_startup.py
  ```

  Expected: the new storage/config tests fail because the adapter and settings fields do not exist yet.

- [ ] **Step 3: Implement the adapter.**

  Add `minio>=7.2,<8` to requirements. Implement MinIO operations with `stat_object`, `get_object`, `put_object`, and `make_bucket`/`bucket_exists`; calculate SHA-256 while reading/writing. Convert SDK errors into `StorageUnavailable` or `StorageObjectNotFound`. Reject empty keys, leading `/`, and keys containing `..`. Extend `Settings` with the MinIO and internal-token values and keep existing constructor overrides so tests do not require a running MinIO.

- [ ] **Step 4: Run focused tests and the existing OCR suite.**

  Run:

  ```bash
  .venv/bin/pytest -q tests/test_object_storage.py tests/test_startup.py tests/test_ocr_api.py
  ```

  Expected: all selected tests pass and `OCR_ENGINE=mock` startup remains available.

---

### Task 2: SQLite 对象引用和任务存储兼容层

**Files:**
- Modify: `ocr-service/app/store.py`
- Modify: `ocr-service/app/models.py`
- Test: `ocr-service/tests/test_storage_persistence.py`
- Test: `ocr-service/tests/test_ocr_api.py`

**Interfaces:**
- Add internal `StorageReferences` value object with `source_bucket`, `source_object_key`, `preview_bucket`, `preview_object_key` and `storage_version`.
- Add `TaskStore.put(task, references)` and `TaskStore.get_with_references(task_id)`; retain `get(task_id)` and `list_tasks()` return types for existing callers.
- Public `OcrTask`, `SourceFile` and `DetectionPreview` JSON remains unchanged; object keys are stored in SQLite columns and never serialized by `dump_model`.

- [ ] **Step 1: Write failing persistence tests.**

  Cover a new database and an old database without the new columns:

  ```python
  def test_task_store_persists_internal_object_keys_without_public_response_fields(tmp_path):
      store = TaskStore(tmp_path)
      task = sample_task()
      store.put(task, StorageReferences(
          source_bucket="hr-platform",
          source_object_key="archive/employee/1/a.png",
          preview_bucket="hr-platform",
          preview_object_key="ocr/preview/task.png",
          storage_version=1,
      ))
      loaded, refs = store.get_with_references(task.task_id)
      assert refs.source_object_key == "archive/employee/1/a.png"
      assert "sourceObjectKey" not in dump_model(loaded)
  ```

  Also assert initialization adds missing columns to an old `ocr_tasks` table and preserves the old payload.

- [ ] **Step 2: Run focused tests to verify the expected failure.**

  ```bash
  .venv/bin/pytest -q tests/test_storage_persistence.py
  ```

- [ ] **Step 3: Implement schema compatibility.**

  Keep the existing `payload` and `updated_at` columns. On initialization, inspect `PRAGMA table_info(ocr_tasks)` and add the five internal columns if absent. Use parameterized SQL for all values. When loading an old row, return references with `None` keys so the service can use the legacy local-file migration path.

- [ ] **Step 4: Run focused and regression tests.**

  ```bash
  .venv/bin/pytest -q tests/test_storage_persistence.py tests/test_ocr_api.py tests/test_field_extraction.py
  ```

---

### Task 3: OCR 识别、预览和 multipart/对象引用双模式

**Files:**
- Modify: `ocr-service/app/main.py`
- Modify: `ocr-service/app/service.py`
- Modify: `ocr-service/app/store.py`
- Modify: `ocr-service/app/visualization.py` only where temporary-file handling requires it
- Modify: `ocr-service/app/config.py`
- Test: `ocr-service/tests/test_ocr_api.py`
- Test: `ocr-service/tests/test_storage_persistence.py`

**Interfaces:**
- `POST /api/ocr/tasks` accepts either `file` or the internal object-reference fields `sourceObjectKey`, `originalName`, `contentType`, `size`, `sha256`.
- Object-reference requests must include `X-OCR-Internal-Token` matching `Settings.internal_token`.
- `OcrService.create_task` resolves a `SourceFile` plus `StorageReferences`, downloads the source to a `TemporaryDirectory`, runs the existing engine, uploads the preview to MinIO, stores the task, and removes all temporary files in success and failure paths.
- Direct multipart uploads use `ocr/source/{sha256}{extension}`; archive references use the received `archive/...` key.

- [ ] **Step 1: Add red tests for both modes and no local image writes.**

  Extend the fake storage API tests:

  ```python
  def test_multipart_upload_persists_binary_in_minio_only(tmp_path):
      client = create_test_client(data_dir=tmp_path, storage=InMemoryObjectStorage())
      response = client.post("/api/ocr/tasks", files={"file": ("a.png", b"png", "image/png")})
      assert response.status_code == 200
      assert not list(tmp_path.glob("*.png"))
      assert not (tmp_path / "previews").exists()
  
  def test_archive_object_reference_uses_existing_minio_source_without_copy():
      storage = InMemoryObjectStorage({"archive/employee/1/a.png": png_bytes})
      response = post_object_reference(client, token="test-token", key="archive/employee/1/a.png")
      assert response.status_code == 200
      assert storage.keys() == {"archive/employee/1/a.png", "ocr/preview/<task-id>.png"}
  
  def test_object_reference_requires_internal_token():
      response = post_object_reference(client, token="wrong", key="archive/employee/1/a.png")
      assert response.status_code == 403
  ```

  Add tests for invalid prefix, missing source object, preview regeneration, and temporary-file cleanup after engine failure.

- [ ] **Step 2: Run the red tests.**

  ```bash
  .venv/bin/pytest -q tests/test_ocr_api.py tests/test_storage_persistence.py
  ```

- [ ] **Step 3: Implement source and preview storage.**

  Make `UploadFile` optional at the route boundary and reject requests that provide neither mode or both modes. For multipart content, hash bytes, use the content-addressed source key, and skip `put_bytes` when an identical object already exists. For object references, verify the token and `archive/` prefix, stat the object, and verify supplied size/hash when present. Use `TemporaryDirectory` for Paddle input and preview rendering. Store the preview object key in SQLite and make the preview endpoint read MinIO bytes. Keep `DETECTION_PREVIEW_NOT_FOUND` semantics when source/preview evidence is unavailable.

- [ ] **Step 4: Run the complete OCR suite.**

  ```bash
  .venv/bin/pytest -q
  ```

  Expected: existing OCR contract, field extraction, Paddle adapter and startup tests pass, with new tests proving `data` contains SQLite metadata only.

---

### Task 4: archive-service 改为传递 MinIO 对象引用

**Files:**
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrClient.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/HttpOcrClient.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveService.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/storage/ObjectStorage.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/storage/MinioObjectStorage.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/config/ArchiveExternalConfiguration.java`
- Modify: `backend/archive-service/src/main/resources/application.yml`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/ocr/HttpOcrClientTest.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/document/ArchiveServiceTest.java`

**Interfaces:**
- Add `OcrSourceData(objectKey, originalName, contentType, size, sha256)` as a Java record in `com.hrplatform.archive.ocr`.
- Change `OcrClient.createTask` to `createTask(OcrSourceData source, String documentType)`.
- Add `ObjectStorage.physicalKey(String logicalKey)` so archive-service can pass the actual `archive/...` key without exposing storage implementation details elsewhere.
- `HttpOcrClient` sends no `file` part for archive OCR; it sends `sourceObjectKey`, `originalName`, `contentType`, `size`, `sha256` and `X-OCR-Internal-Token`.

- [ ] **Step 1: Write failing Java tests.**

  Update `HttpOcrClientTest` to assert the request body contains the object-reference form fields and does not contain a multipart file body. Update `ArchiveServiceTest` with a fake object storage whose `get()` throws if `runOcr` calls it; assert the object reference contains the qualified `archive/...` key.

- [ ] **Step 2: Run focused tests to verify the old byte-array contract fails the new assertions.**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 17) \
    mvn -f backend/pom.xml -pl archive-service -am \
    -Dtest=HttpOcrClientTest,ArchiveServiceTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```

- [ ] **Step 3: Implement the reference contract.**

  Remove the `objectStorage.get()` call from `ArchiveService.runOcr`. Add the internal token property under `archive.ocr-internal-token` with environment override `OCR_INTERNAL_TOKEN`. Add the header only for object-reference requests. Keep `ObjectStorage.get()` unchanged for user-controlled archive downloads.

- [ ] **Step 4: Run archive regression tests.**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 17) \
    MYSQL_USERNAME=hr MYSQL_PASSWORD='change-me-application-password' \
    MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
    MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
    mvn -f backend/pom.xml -pl archive-service -am test
  ```

---

### Task 5: 历史本地 OCR 存储迁移工具

**Files:**
- Create: `ocr-service/scripts/migrate_local_storage.py`
- Create: `ocr-service/tests/test_storage_migration.py`
- Modify: `ocr-service/app/store.py` if import/export helpers are needed
- Modify: `ocr-service/README.md`

**Interfaces:**
- Command supports `--data-dir`, `--legacy-data-dir`, `--dry-run` and `--delete-local-binaries`.
- Default behavior is dry-run-like safe migration: upload/merge and verify, never delete.
- Exit code is non-zero when a referenced local file is missing, object hash verification fails, or SQLite merge cannot complete.

- [ ] **Step 1: Write migration tests.**

  Build two temporary data directories containing one current task, one legacy task, duplicate task IDs and a missing file. Assert:

  - dry-run performs no upload and no deletion;
  - successful migration uploads source and preview objects and is idempotent on the second run;
  - missing files return failure and keep every source file;
  - `--delete-local-binaries` deletes only files whose MinIO object hash was verified;
  - SQLite metadata is merged into the active `data/ocr_tasks.sqlite3` without removing field revision history.

- [ ] **Step 2: Run migration tests and verify they fail before the script exists.**

  ```bash
  .venv/bin/pytest -q tests/test_storage_migration.py
  ```

- [ ] **Step 3: Implement idempotent migration.**

  Read task payloads from both SQLite files, choose the newest row for duplicate task IDs, locate legacy source/preview files using the existing safe filename rules, upload to `ocr/source/{sha256}{ext}` and `ocr/preview/{taskId}.png`, then write internal references through `TaskStore`. Before deletion, read the MinIO object back and compare SHA-256 and byte size. Never remove SQLite files automatically; print the exact paths eligible for manual removal.

- [ ] **Step 4: Run migration tests and a dry-run against the current project data.**

  ```bash
  .venv/bin/pytest -q tests/test_storage_migration.py
  .venv/bin/python scripts/migrate_local_storage.py \
    --data-dir data --legacy-data-dir app/data --dry-run
  ```

  Expected: the report lists source files, preview files, duplicate task IDs, missing files and eligible deletion paths without changing project data.

---

### Task 6: 配置、文档与端到端验收

**Files:**
- Modify: `ocr-service/.env.example`
- Modify: `ocr-service/README.md`
- Modify: `backend/README.md`
- Modify: `docs/数据库设计.md`
- Modify: `docs/superpowers/specs/2026-09-20-microservice-data-contract-design.md`
- Modify: `scripts/verify-platform.sh`
- Test: `ocr-service/tests/test_startup.py`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/config/ArchiveExternalConfigurationTest.java`

- [ ] **Step 1: Add configuration documentation and startup checks.**

  Document these variables and their roles:

  ```dotenv
  MINIO_ENDPOINT=http://127.0.0.1:19000
  MINIO_ACCESS_KEY=minioadmin
  MINIO_SECRET_KEY=change-me-minio-password
  MINIO_BUCKET=hr-platform
  MINIO_ALLOWED_SOURCE_PREFIX=archive/
  OCR_INTERNAL_TOKEN=local-ocr-internal-token
  ```

  Keep the existing default-port examples for clean installations and show the current `19000/19001` local mapping separately.

- [ ] **Step 2: Extend platform verification.**

  After OCR success, check the task result and MinIO object listing/API so the integrated path has one `archive/...` source object and one `ocr/preview/...` object. Check that `ocr-service/data` has no PNG/JPEG files or `previews` directory. Do not make the verifier delete data.

- [ ] **Step 3: Run all automated checks.**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml test
  cd ocr-service && .venv/bin/pytest -q
  cd ../frontend && npm test -- --run && npm run build
  ```

- [ ] **Step 4: Run platform verification with current ports.**

  ```bash
  MYSQL_USERNAME=hr \
  MYSQL_PASSWORD='change-me-application-password' \
  MYSQL_ROOT_PASSWORD='change-me-root-password' \
  MYSQL_PRIMARY_PORT=13306 MYSQL_REPLICA_PORT=13307 REDIS_PORT=16379 \
  GATEWAY_BASE_URL=http://127.0.0.1:18080 \
  IDENTITY_BASE_URL=http://127.0.0.1:18081 \
  ARCHIVE_BASE_URL=http://127.0.0.1:18082 \
  WORKFLOW_BASE_URL=http://127.0.0.1:18083 \
  OCR_BASE_URL=http://127.0.0.1:8000 \
  FRONTEND_URL=http://127.0.0.1:5173 \
  MINIO_HEALTH_URL=http://127.0.0.1:19000/minio/health/live \
  SECURITY_JWT_SECRET='local-development-secret-change-me-please-32' \
  SECURITY_JWT_ISSUER=hr-platform \
  bash scripts/verify-platform.sh
  ```

  Expected: existing platform checks plus object-count and local-binary checks pass.

- [ ] **Step 5: Perform the real migration only after approval of the dry-run report.**

  Back up both directories, stop OCR writes, run the migration without deletion, restart OCR, verify representative historical task queries and previews, then rerun with `--delete-local-binaries`. Keep `ocr-service/data/ocr_tasks.sqlite3`; remove `ocr-service/app/data` only after the migration report shows no remaining legacy task or binary dependency.

## Self-review checklist

- The plan covers new MinIO storage, archive-service contract changes, SQLite compatibility, historical migration and platform verification.
- The public OCR API path and response shape remain unchanged.
- No task deletes local data before a verified MinIO read-back.
- No service crosses the archive database boundary.
- All planned tests have an explicit command and expected behavior.
