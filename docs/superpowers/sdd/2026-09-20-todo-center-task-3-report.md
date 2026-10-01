# P1 待办中心 Task 3 实施报告

日期：2026-09-20
模块：workflow-service

## 状态

已完成并通过独立任务级复核。workflow-service 现在聚合 HR 审批、档案访问审批、档案归还到期和 OCR 失败摘要，使用 JWT DataScope 限制范围，并通过 Redis 做 30 秒 cache-aside。

## 实现内容

- 新增 `GET /api/todos?limit=1..100`，返回统一 `ApiResponse<TodoData>`。
- 新增 workflow_db 内部待办 mapper，覆盖 pending HR request、pending archive access 和 `IN_USE` 归还到期记录，使用确定性排序和 DataScope 条件。
- 新增带原始 Bearer JWT 的 archive-service 内部 HTTP 客户端，配置连接/读取超时；401、403、404 映射为范围拒绝，连接或读取失败映射为 `TODO_SOURCE_UNAVAILABLE`。
- 使用 `CacheKeys.todo(userId, scopeKey)`，缓存 TTL 固定 30 秒；Redis 读写或 JSON 解析失败时回源，不缓存异常和部分响应。
- workflow 主配置显式声明 `spring.data.redis` 的 host、port 和 timeout，运行环境可通过 `REDIS_HOST`、`REDIS_PORT`、`REDIS_TIMEOUT` 注入，避免待办缓存回落到默认端口。
- cache miss 始终回源最多 100 条并写入完整摘要，响应端再按请求 limit 截断，避免同一 scope 先请求小 limit 后无法获得更大 limit 的缓存缺陷。
- `TodoExceptionHandler` 限定在 todo controller 所属包，避免覆盖其他 workflow controller 的异常映射。

## TDD RED

独立复核时先增加回归测试 `cacheMissWarmsScopeCacheWithMaximumLimitSoLaterLargerRequestsDoNotLoseItems`，随后运行 focused 命令，按预期失败：

```text
Tests run: 8, Failures: 1, Errors: 0, Skipped: 0
expected: 2 but was: 0
```

失败原因是原实现按请求的 `50` 查询，测试预期统一缓存上限 `100`，因此未能命中 `100` 参数的 mock。该失败验证了缓存键不含 limit 时的真实边界问题。

## GREEN

修正服务为 cache miss 使用上限 100 回源、写入完整结果，再按请求 limit 截断；同步更新 focused 断言。命令：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml \
  -pl workflow-service -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=TodoServiceTest,TodoControllerTest,TodoPersistenceTest,HttpArchiveTodoClientTest test
```

结果：TodoPersistenceTest 2/2、TodoControllerTest 4/4、HttpArchiveTodoClientTest 1/1、TodoServiceTest 8/8；共 15 个测试，Failures 0、Errors 0、Skipped 0，`BUILD SUCCESS`。

## 回归结果

命令：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/workflow_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/workflow_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
mvn -f backend/pom.xml -pl workflow-service -am \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：platform-common-web 11、platform-common-datasource 4、platform-common-security 3、workflow-service 48（含 Redis 配置契约测试）；全部 Failures 0、Errors 0、Skipped 0，`BUILD SUCCESS`。

## 变更文件

- `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/ArchiveTodoClient.java`
- `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/HttpArchiveTodoClient.java`
- `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoController.java`
- `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoDtos.java`
- `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoExceptionHandler.java`
- `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoMapper.java`
- `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoService.java`
- `backend/workflow-service/src/main/java/com/hrplatform/workflow/todo/TodoArchiveUnavailableException.java`
- `backend/workflow-service/src/main/resources/mapper/TodoMapper.xml`
- `backend/workflow-service/src/main/resources/application.yml`
- `backend/workflow-service/src/main/resources/application-test.yml`
- `backend/workflow-service/src/test/java/com/hrplatform/workflow/todo/HttpArchiveTodoClientTest.java`
- `backend/workflow-service/src/test/java/com/hrplatform/workflow/todo/TodoControllerTest.java`
- `backend/workflow-service/src/test/java/com/hrplatform/workflow/todo/TodoPersistenceTest.java`
- `backend/workflow-service/src/test/java/com/hrplatform/workflow/todo/TodoServiceTest.java`
- `docs/superpowers/sdd/2026-09-20-todo-center-task-3-report.md`

## 自审与遗留风险

- 查询条件只使用认证 JWT 派生的 DataScope，不接受客户端 scope 参数；`NONE` 返回空列表。
- Redis 缓存只保存 TodoData 摘要，不保存令牌、对象 key、原图、完整 OCR JSON、检测框 PNG 或文件路径。
- `TodoPersistenceTest` 增加 `@AfterEach` 清理，避免测试数据污染后续回归。
- 当前 OCR 失败摘要的 `error_message` 写入链路仍是 Task 2 已记录的后续风险；Task 3 只消费 archive-service 内部摘要接口，未跨库查询。
- 未执行 Git 提交；工作区不是 Git 仓库，也未生成 SHA。
