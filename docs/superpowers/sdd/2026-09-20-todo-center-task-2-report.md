# P1 待办中心 Task 2 实施报告

日期：2026-09-20
模块：archive-service

## 状态

已完成。新增带 JWT DataScope 的内部 OCR 失败摘要接口，并完成 focused 测试与 archive-service 回归验证。

接口：`GET /internal/archive/ocr-failures?limit={limit}`

实现要点：

- 使用 `@PreAuthorize("hasAuthority('PERM_ARCHIVE_READ')")` 保护接口。
- 从认证主体的 `JwtPrincipal` 派生 `DataScope`，不接受查询参数中的 scope。
- limit 在 Controller 中限制为 1–100，默认 20。
- Mapper 仅返回 `FAILED` 绑定，按 `updated_at DESC, id DESC` 排序，并 join active employee、archive record、archive document。
- SQL 显式覆盖 `ALL`、`DEPARTMENT`、`EMPLOYEE` 和 `NONE` scope；未授权/未知 scope 走空结果。
- 返回字段仅包含 bindingId、versionId、employeeId、taskId、errorMessage、updatedAt，不包含 object key 或 OCR 文件路径。

## TDD RED

### 简报原始 RED 命令

命令：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
mvn -f backend/pom.xml -pl archive-service -am \
  -Dtest=OcrFailurePersistenceTest,ArchiveAuthorizationControllerTest test
```

关键输出：

```text
[ERROR] Failed to execute goal ... maven-surefire-plugin:3.5.3:test ...
[ERROR] No tests matching pattern "OcrFailurePersistenceTest, ArchiveAuthorizationControllerTest" were executed!
[ERROR] Reactor: platform-common-core FAILURE; archive-service SKIPPED
[ERROR] BUILD FAILURE
```

该命令在 reactor 上游 `platform-common-core` 没有指定测试时提前失败，未到达 archive-service。该环境阻塞已原样保留，没有将其伪造为目标测试结果。

### 绕过 reactor 无匹配测试后的 RED 确认

为确认目标测试确实因功能缺失失败，使用同一 focused 选择增加 Surefire 的上游兼容参数：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
mvn -f backend/pom.xml -pl archive-service -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=OcrFailurePersistenceTest,ArchiveAuthorizationControllerTest test
```

关键输出：

```text
[ERROR] Compilation failure
[ERROR] cannot find symbol: class OcrFailureData
[ERROR] cannot find symbol: method listFailuresInScope(...)
[ERROR] constructor ArchiveAuthorizationController(...) cannot be applied to given types
[ERROR] archive-service FAILURE
```

RED 原因是目标 DTO、Mapper 查询和 Controller endpoint 尚不存在，符合功能缺失预期。

## GREEN

### 简报原始 focused GREEN 命令

实现后按简报原命令重跑，输出仍为同一 reactor 配置阻塞：

```text
[ERROR] No tests matching pattern "OcrFailurePersistenceTest, ArchiveAuthorizationControllerTest" were executed!
[ERROR] platform-common-core FAILURE; archive-service SKIPPED
[ERROR] BUILD FAILURE
```

### 有效 focused GREEN 命令

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
mvn -f backend/pom.xml -pl archive-service -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=OcrFailurePersistenceTest,ArchiveAuthorizationControllerTest test
```

关键输出：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- OcrFailurePersistenceTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- ArchiveAuthorizationControllerTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
archive-service SUCCESS
BUILD SUCCESS
```

Flyway 输出确认：

```text
Successfully validated 2 migrations
Current version of schema `archive_db`: 2
Schema `archive_db` is up to date. No migration necessary.
```

## 回归结果

命令：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
mvn -f backend/pom.xml -pl archive-service -am test
```

结果：

```text
platform-common-web: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
platform-common-datasource: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
platform-common-security: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
archive-service: Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
archive-service SUCCESS
BUILD SUCCESS
```

## 变更文件

- `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrFailureData.java`
- `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrBindingMapper.java`
- `backend/archive-service/src/main/resources/mapper/OcrBindingMapper.xml`
- `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveAuthorizationController.java`
- `backend/archive-service/src/main/resources/db/migration/V2__ocr_todo_index.sql`
- `backend/archive-service/src/test/java/com/hrplatform/archive/ocr/OcrFailurePersistenceTest.java`
- `backend/archive-service/src/test/java/com/hrplatform/archive/api/ArchiveAuthorizationControllerTest.java`
- `docs/superpowers/sdd/2026-09-20-todo-center-task-2-report.md`

未修改 `V1__archive_schema.sql`、workflow-service、frontend 或 OCR service；未添加 gateway route；未提交 Git，也未生成 SHA。

## 自审

- `OcrFailureData` 使用 record，MyBatis `failureMap` 使用 constructor 映射，已覆盖真实数据库查询路径，避免 setter ReflectionException。
- Persistence 测试写入两个部门的 failed binding，并确认部门 scope 只返回本部门记录及其 errorMessage。
- Controller 测试确认统一 `ApiResponse` envelope、JWT department scope 参数、limit 上限和 `PERM_ARCHIVE_READ` 注解。
- 查询只选摘要字段，未读取 object key、文件路径或 OCR 文件内容。
- V2 是针对 V1 schema gap 的兼容迁移：新增可空 `error_message` 列，并新增 `(status, updated_at, id)` 索引支持失败状态排序和稳定 tie-break。
- 完整回归包含原 archive-service 测试，未发现回归失败。

## Concerns

1. V1 原本缺少 `error_message`，已由 V2 以可空列补齐，避免修改已发布迁移。
2. 本任务禁止修改 `OcrBinding` 和 `ArchiveService`，现有失败绑定创建链路没有可传入的 `errorMessage` 属性；因此本接口能正确读取已写入 `ocr_binding.error_message` 的失败摘要，新产生的失败绑定是否填充错误文本取决于后续允许范围内的写入链路补充。Task 2 未越界修改该服务路径。
3. 简报原始 focused 命令受 reactor Surefire 无匹配测试行为影响；报告中的 effective focused 命令仅增加 `-Dsurefire.failIfNoSpecifiedTests=false`，目标测试结果为真实执行结果。

## Task 2 审查修复（2026-09-20）

### RED

先在 `ArchiveAuthorizationControllerTest` 增加 `limit=0` 测试，并为 standalone MockMvc 注册 `GlobalExceptionHandler`，随后运行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' mvn -f backend/pom.xml -pl archive-service -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ArchiveAuthorizationControllerTest test
```

结果为预期 RED：新增测试失败，`Status expected:<400> but was:<200>`。失败原因是原控制器将 `limit=0` 静默规整为 1。

### GREEN

实现 `limit < 1` 抛出 `IllegalArgumentException("limit must be positive")`，保留 `limit > 100` 截断为 100。扩展真实 MySQL `OcrFailurePersistenceTest`，保留 department 隔离并新增 `ALL`、`EMPLOYEE`、`NONE` scope，验证非 ACTIVE employee/archive_record/document、非 FAILED binding 被排除，验证 `updated_at DESC, id DESC` 和 SQL LIMIT 截断；所有夹具数据在 `@AfterEach` 清理。

focused 命令：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' mvn -f backend/pom.xml -pl archive-service -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=OcrFailurePersistenceTest,ArchiveAuthorizationControllerTest test
```

结果：`OcrFailurePersistenceTest` 2/2、`ArchiveAuthorizationControllerTest` 4/4，全部通过，`BUILD SUCCESS`。其中 `limit=0` 返回 HTTP 400 和 `VALIDATION_ERROR`，`limit=1000` 仍向 mapper 传入 100。

### 回归

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' mvn -f backend/pom.xml -pl archive-service -am -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：platform-common-web 11、platform-common-datasource 4、platform-common-security 3、archive-service 40；共 58 个测试，Failures 0、Errors 0、Skipped 0，`BUILD SUCCESS`。

### 本次变更文件

- `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveAuthorizationController.java`
- `backend/archive-service/src/test/java/com/hrplatform/archive/api/ArchiveAuthorizationControllerTest.java`
- `backend/archive-service/src/test/java/com/hrplatform/archive/ocr/OcrFailurePersistenceTest.java`
- `docs/superpowers/sdd/2026-09-20-todo-center-task-2-report.md`

### Concerns

- 未修改 mapper SQL、DTO、迁移、workflow、frontend 或 OCR service；查询规则由行为测试锁定。
- standalone MockMvc 仅测试控制器自身能力，因此显式注册 `GlobalExceptionHandler` 以验证生产环境现有的 400/`VALIDATION_ERROR` 异常体系。
- 工作区不是 Git 仓库；未提交，也未生成或伪造 SHA。
