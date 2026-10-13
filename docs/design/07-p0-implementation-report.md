# P0 偏差关闭实现报告

> 日期：2026-10-13
> 范围：00-completion-review.md 列出的 7 处 P0 偏差，本次关闭其中 6 处（读写分离按 ADR-003 定稿为"不做"，不属代码缺口）。
> 方法：TDD（红→绿），不破坏既有测试基线。

## 测试基线对比

| 套件 | 改造前 | 改造后 | 新增 | 跳过 | 状态 |
|---|---|---|---|---|---|
| 后端 `mvn test`（Java 17） | 215 | **249** | +34 | 5（MySQL 环境变量） | ✅ BUILD SUCCESS |
| 前端 `npm test -- --run` | 38 | **38** | 0 | 0 | ✅ 14 文件全过 |
| OCR `pytest -q` | 56 | **62** | +6 | 0 | ✅ 全过（本地副本验证；iCloud 项目目录因驱逐有 4 个 migration 测试未复跑） |

---

## P0-1：请假状态闭环 ✅

### 问题
`EmployeeStatus.ON_LEAVE` 枚举存在但 `EmployeeChangeService.applyEmployeeChange` 的 LEAVE 分支走默认保留原状态；请假审批通过后员工永远显示在职。

### 改动

**新增 Flyway 迁移**
- `V19__leave_effective_dates.sql`：`leave_request_detail` 加 `effective_from DATE NULL`、`effective_to DATE NULL`，加索引 `idx_leave_request_effective_window`。历史行不回填、保持 NULL，调度器跳过 NULL。

**新增/修改类**
| 文件 | 改动 |
|---|---|
| `workflow/LeaveStatusWindow.java` | 新增 record |
| `workflow/RequestMapper.java` + `RequestMapper.xml` | 新增 `markLeaveEffectiveWindow`（幂等 UPDATE）、`listLeaveStatusWindowsForSync`、`countOtherActiveLeaveWindows` |
| `workflow/EmployeeChangeService.java` | 新增 `markLeaveEffectiveWindow`（LEAVE 审批通过写窗口）+ `syncLeaveStatus`（窗口内 ACTIVE→ON_LEAVE，结束 ON_LEAVE→ACTIVE，写 `employee_status_history`，幂等） |
| `workflow/EmployeeChangeScheduler.java` | 新增 `@Scheduled(cron=0 5 * * * ?)` 每日扫已批准请假单做状态切换 |
| `workflow/RequestService.java` | LEAVE 到 APPROVED 时调 `markLeaveEffectiveWindow`；OFFBOARDING/TRANSFER 创建校验收紧为仅 ACTIVE（请假期间禁离职/调动） |
| `access/ArchiveAccessService.java` | BORROW/HANDOVER 外借时校验档案属主状态，ON_LEAVE 抛"档案已封存"；READ 不受影响 |
| `application-test.yml` | 加 `hr.workflow.leave-status-cron: "-"` 禁用测试期调度 |

### 新增测试（12 个）
- `EmployeeChangeServiceLeaveTest`（7）：写窗口/忽略非 LEAVE/窗口内入休假/窗口结束销假/幂等不重复写历史/重叠请假不提前销假/窗口未开始不动
- `RequestServiceTest`（+2）：ON_LEAVE 员工禁发起离职、禁发起调动
- `ArchiveAccessServiceTest`（+2）：ON_LEAVE 员工档案 BORROW 被拒、READ 放行
- `WorkflowMigrationTest`（+1）：断言 V19 含两列与索引

### 限制
- 状态切换由每日 05:05 cron 驱动，当天请假开始不会立刻切（演示可手动调 `applyLeaveStatusTransitions()`）
- 档案封存校验按"档案属主员工"判定，而非申请人本人

---

## P0-2：OCR 结果确认回填 ✅

### 问题
OCR 有结果查询和字段修订接口，但没有"确认已修订结果并回填正式档案"接口；修订值只留在 `ocr_field_revision`，不回写 employee 主档。

### 改动

**新增 Flyway 迁移**
- `V20__ocr_confirm_status.sql`：`ocr_binding` 加 `confirm_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'`、`confirmed_by BIGINT NULL`、`confirmed_at TIMESTAMP NULL`、`confirm_note VARCHAR(512) NULL`，加索引 `idx_ocr_binding_confirm_status`。

**新增/修改类**
| 文件 | 改动 |
|---|---|
| `ocr/OcrConfirmResult.java` | 新增 record(bindingId, confirmStatus, employeeId, employeeUpdated) |
| `ocr/OcrConfirmService.java` | 核心服务（@Transactional）：状态机 PENDING→CONFIRMED/REJECTED；字段白名单（employee_name/employee_id/phone/id_number）；只回写 name+phone，id_number 按数据最小化不回写；低置信度字段必须已人工修订且 PASSED；幂等；审计写前后值；行锁防并发 |
| `ocr/OcrBinding.java` | record 加 4 个 confirm 字段，保留原便利构造器 |
| `ocr/OcrBindingMapper.java` + `.xml` | resultMap/列加 4 列；新增 `updateConfirmStatus` |
| `ocr/OcrService.java` | 全参构造补 4 个新参；`withId()` 重试时保留 confirm 状态 |
| `ocr/OcrController.java` | 新增两个 HR_ADMIN 端点：`POST /archive/ocr-bindings/{id}/confirm`、`POST /archive/ocr-bindings/{id}/reject` |

### 新增测试（15 个）
- `OcrConfirmServiceTest`（12）：状态机、白名单、低置信度拒绝、幂等、审计、行锁
- `OcrConfirmControllerTest`（3）：端点委托、权限校验

### 限制
- 前端"确认/拒绝"按钮未加（留给后续 UI ticket）
- 纯 Mockito 单测，真实 MySQL 下 mapper 行为依赖 V20 迁移在 `HR_MYSQL_MIGRATION_TEST=1` 时验证
- `employee_id`（工号）不回写 `employee.employee_no`（唯一键冲突风险）

---

## P0-3：OCR 服务可靠性边界 ✅

### 问题
FastAPI `async def` 路由但函数体内同步执行 PaddleOCR 推理 + MinIO 上传 + SQLite 写入，阻塞事件循环；无并发上限、无超时、无失败状态、启动时悬挂任务永久 RUNNING。

### 改动（均在 `ocr-service/`）
| 文件 | 改动 |
|---|---|
| `app/config.py` | 新增 `OCR_TASK_TIMEOUT_SECONDS`（默认 60）、`OCR_MAX_CONCURRENT_TASKS`（默认 2） |
| `app/models.py` | `OcrTask.status` 枚举新增 `INTERRUPTED` |
| `app/service.py` | `create_task` 拆成 `begin_task`（落库 RECOGNIZING）+ `run_task_recognition`（下载/推理/映射）；新增 `find_succeeded_by_sha256`、`mark_recognition_failed`、`recover_interrupted_tasks` |
| `app/main.py` | 同 SHA-256 幂等；`asyncio.to_thread` 隔离推理；`asyncio.Semaphore(2)` 限并发；`asyncio.wait_for(60s)` 超时；lifespan 启动时 `recover_interrupted_tasks()` |
| `tests/test_service_reliability.py` | 新增 6 个测试 |
| `tests/test_ocr_api.py` | openapi 枚举补 INTERRUPTED；既有同图双传测试改用不同图 |

### 新增测试（6 个）
1. 同内容重复上传返回已有 taskId
2. 不同内容产生不同任务
3. 注入慢引擎 + 0.3s 超时，任务落 FAILED
4. 并发 4 请求，峰值并发 ≤2
5. 残留 RECOGNIZING 任务重启后变 INTERRUPTED
6. INTERRUPTED 在 openapi schema 中声明

### 限制
- 超时无法强杀 PaddleOCR 推理线程（用 abandoned 事件让线程跑完不回写 SUCCEEDED）
- 同 hash 幂等是 `list_tasks()` 全表扫描 O(n)
- iCloud 项目目录因文件驱逐，4 个 migration 测试未在本地复跑（代码字节与本地验证副本一致）

---

## P0-4：Redis 审批幂等键 ✅

### 问题
Redis 容器在跑但业务代码零使用。

### 改动
| 文件 | 改动 |
|---|---|
| `common/idempotency/IdempotencyService.java` | 新增。注入 `@Autowired(required=false) StringRedisTemplate`，`tryAcquire` 走 `SET NX EX`；Redis 不可用时 warn 日志并放行（降级） |
| `common/idempotency/IdempotentReplayException.java` | 新增。message="请勿重复提交" |
| `common/web/GlobalExceptionHandler.java` | 把 `IdempotentReplayException` 映射到 409 STATE_CONFLICT |
| `workflow/RequestService.java` | approve/reject 在状态变更前调 `requireIdempotentApproval`；key=`idem:{approve\|reject}:{userId}:{requestId}`，TTL 10s；`idempotencyService==null` 时跳过（旧测试零改动） |

### 新增测试（7 个）
- `IdempotencyServiceTest`（4）：SET NX EX 正确调用；首次 true/重复 false；Redis 异常→放行；无 template→放行
- `RequestServiceIdempotencyTest`（3）：approve 被拦截；reject 被拦截；TTL 确为 10s

### 限制
- 审批通过后业务失败不回滚幂等键（10s 窗口内不可重试）——演示级取舍
- 降级路径返回 true（放行），不是内存 map——选择最简单方案，保证本地无 Redis 也能起

---

## P0-5：权限模型单一来源 ✅

### 问题
`sys_permission` / `sys_role_permission` 表建了但代码不读，接口靠硬编码 `@PreAuthorize`。

### 改动
- **文档**：`docs/design/02-architecture.md` 新增 ADR-008（固定角色注解为唯一权限来源，DB 权限表仅作未来扩展预留）；更新 ADR-004（Redis 从"待定"改为"已落地审批幂等"）；刷新"现状 vs 目标"偏差表，6 项已关闭标 ✅。
- **前端**：`web/src/views/AdminUsersView.vue` 页面描述加一行小字："角色为固定枚举（EMPLOYEE / DEPT_MANAGER / HR_ADMIN / SYSTEM_ADMIN），接口权限由后端注解统一控制；数据库权限表仅作未来扩展预留，当前不支持细粒度权限配置。"
- **后端 security 代码未改**（按要求做减法）。

---

## 仍存在的限制（诚实记录）

1. **前端 OCR 确认/拒绝按钮未加**：后端端点已就绪（POST /confirm /reject），但 OcrReviewView 还没有"确认回填档案"按钮。需要下一轮 UI ticket 补。
2. **请假状态切换不是实时**：每日 cron 驱动，当天请假开始不会立刻切 ON_LEAVE。演示时可手动触发。
3. **OCR 超时无法强杀推理线程**：abandoned 事件让线程跑完不回写，后台线程仍占用到推理自然结束。
4. **iCloud 文件驱逐**：OCR venv 在 iCloud 目录下反复被 evict，导致本地无法复跑 pytest；P0-3 子代理在本地副本验证过 62 passed，项目目录字节一致。
5. **未跑四角色浏览器端到端验收**：需要起全套 docker（MySQL/MinIO/Redis/OCR），留给用户本地演示。
6. **未 git commit**：按约束留给用户。
7. **操作日志仍无 AOP 切面**：13 处手动调 `OperationLogService.record()` 保持不变（本科规模可接受）。

## 下一步建议

- 补 OCR 确认/拒绝前端按钮（OcrReviewView）
- 补请假状态在前端的"休假中"徽标
- 合同到期提醒从查询派生改为待办事件（P1 项）
- 四角色浏览器 E2E（Playwright）
