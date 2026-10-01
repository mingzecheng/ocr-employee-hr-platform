# Task 1 实施报告：固化待办接口和 Redis key 契约

> 日期：2026-09-20
> 范围：P1 todo-center Task 1

## 1. 实施结果

已在公共缓存 key 工具中增加 `CacheKeys.todo(long userId, String scopeKey)`：

- 用户 ID 必须大于 0，否则抛出 `IllegalArgumentException`。
- `null`、空字符串和全空白 scope 统一归一化为 `none`；其他 scope 去除首尾空白。
- 使用现有的 SHA-256 和十六进制编码模式，生成 `hr:todo:user:{userId}:{sha256(normalizedScope)}`。
- 未修改既有 `permission`、`idempotent`、`departmentTree`、`archiveList` 和 `archiveListPrefix` 行为。

两份设计文档已同步记录：

- `GET /api/todos?limit=1..100` 公共查询契约。
- `TodoData` 的 `items`、`total`、`pendingApprovalCount`、`dueSoonCount`、`overdueCount`、`ocrFailedCount`、`generatedAt` 字段。
- `TodoItem` 的 `id`、`type`、`title`、`resourceId`、`status`、`priority`、`dueAt`、`createdAt`、`targetPath` 字段。
- 四个允许的待办类型和三个允许的优先级。
- 待办 key 的 30 秒 TTL、scope 隔离和仅保存可重建摘要的约束。

本任务未新增数据库表、迁移或待办生产逻辑，也未缓存 OCR 文件、完整 OCR JSON、检测框 PNG、文件二进制、对象存储 key 或令牌。

## 2. 文件变更

- `backend/platform-common/platform-common-core/src/main/java/com/hrplatform/common/cache/CacheKeys.java`
  - 新增 `todo` key 生成方法。
- `backend/platform-common/platform-common-web/src/test/java/com/hrplatform/common/cache/CacheKeysTest.java`
  - 新建测试，覆盖指定 department scope digest、空 scope 归一化和非正用户 ID。
- `docs/superpowers/specs/2026-09-20-microservice-data-contract-design.md`
  - 增加待办 Redis key 和 HTTP/DTO 契约。
- `docs/数据库设计.md`
  - 增加待办缓存和查询契约。
- `docs/superpowers/sdd/2026-09-20-todo-center-task-1-report.md`
  - 本实施报告。

## 3. RED 证据

先写测试，再运行简报要求的原始聚焦命令：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml \
  -pl platform-common/platform-common-web -am \
  -Dtest=CacheKeysTest test
```

原始命令以退出码 `1` 失败，但 Maven 在 reactor 的 `platform-common-core` 阶段提前停止，没有进入待测模块：

```text
Failed to execute goal ... maven-surefire-plugin:3.5.3:test ...
No tests matching pattern "CacheKeysTest" were executed!
Set -Dsurefire.failIfNoSpecifiedTests=false to ignore this error.
```

为排除该 reactor 测试选择器问题，使用同一命令增加 Maven 建议的临时参数：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml \
  -pl platform-common/platform-common-web -am \
  -Dtest=CacheKeysTest -Dsurefire.failIfNoSpecifiedTests=false test
```

该命令按预期在测试编译阶段失败，输出明确指出生产方法尚不存在：

```text
找不到符号
符号:   方法 todo(long,java.lang.String)
位置: 类 com.hrplatform.common.cache.CacheKeys
6 errors
BUILD FAILURE
```

## 4. GREEN 证据

加入最小 `CacheKeys.todo` 实现后，重新运行带 reactor 修正参数的聚焦测试：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml \
  -pl platform-common/platform-common-web -am \
  -Dtest=CacheKeysTest -Dsurefire.failIfNoSpecifiedTests=false test
```

关键输出：

```text
Running com.hrplatform.common.cache.CacheKeysTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
platform-common-web ................................ SUCCESS
BUILD SUCCESS
```

## 5. common-web 回归证据

按简报要求运行不带 `-Dtest` 的回归命令：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml \
  -pl platform-common/platform-common-web -am test
```

关键输出：

```text
Running com.hrplatform.common.cache.CacheKeysTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Running com.hrplatform.common.cache.IdempotencyServiceTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Running com.hrplatform.common.web.TraceIdFilterTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Running com.hrplatform.common.web.GlobalExceptionHandlerTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
platform-common-web ................................ SUCCESS
BUILD SUCCESS
```

## 6. 自检

- 指定 digest 已验证：`department:12` 生成 `419ea9053c9df9ee9ce641d2b33b3a851e68b346e777bd55f379b330ca232ec4`。
- 空 scope 使用固定 `none`，其 digest 为 `140bedbf9c3f6d56a9846d2ba7088798683f4da0c248231336e6a05679e4fdfe`。
- `0` 和 `-1` 用户 ID 均有测试并被拒绝。
- key 包含 user ID 和 scope digest，scope 不同会生成不同 key；文档明确记录按用户和 scope 隔离。
- 文档字段名和值域与 Task 1 brief 一致。
- 变更未引入数据库表、迁移、POM 依赖或 Task 2/3 的生产代码。
- 当前 workspace 不是 Git repository，因此未执行提交，也未伪造 commit SHA。

## 7. 关注事项

- 按原样执行的聚焦 Maven 命令会因 `-am` 同时对无测试的 `platform-common-core` 应用 `-Dtest=CacheKeysTest` 而提前失败；报告同时保留了该原始输出，并使用 Maven 自身建议的 `-Dsurefire.failIfNoSpecifiedTests=false` 完成实际聚焦 GREEN 验证。该现象不影响不带测试选择器的 common-web 回归命令。
- Maven 编译输出包含已有 `IdempotencyServiceTest.java` 的 unchecked 操作提示，以及测试运行时的 JVM class-sharing warning；两者均未导致失败，本任务未修改相关文件。
