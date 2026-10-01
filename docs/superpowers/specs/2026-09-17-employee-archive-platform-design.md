# 员工档案与人事流程平台设计规格

## 文档状态

- 版本：1.0
- 日期：2026-09-17
- 当前阶段：需求与技术设计，尚未进入代码实现
- 审核基线：本文件与 `docs/需求理解.md`、`docs/方案设计.md` 的 2026-09-17 追加章节共同构成当前设计基线

## 目标

建设一个面向企业员工档案和人事流程的核心微服务平台，完成登录权限、员工与组织、档案材料版本管理、OCR 识别与证据留存、审批、档案使用归还、盘点异常和审计统计。首期重点是打通以下可演示闭环：

```text
登录与权限 -> 员工/档案建档 -> 图片上传 -> OCR 识别
-> 检测框预览 -> 字段复核修订 -> 版本发布 -> 访问申请与审计
```

## 方案选择

### 方案 A：核心微服务（推荐）

拆分 `gateway-service`、`identity-service`、`archive-service`、`workflow-service`，复用现有 `ocr-service`。三个业务库共享一组 MySQL 主从集群，Redis 负责缓存和幂等。服务边界与核心业务域一致，能展示微服务、读写分离和 OCR 独立部署，同时控制运行单元数量。

### 方案 B：模块化单体 + 独立 OCR

所有 Java 业务模块放进一个 Spring Boot 应用，只有 OCR 独立。开发和事务最简单，但不能完整体现用户明确要求的核心微服务架构，服务边界和独立数据归属也不明显。

### 方案 C：按表或按功能拆分的细粒度微服务

把用户、部门、员工、档案、文件、审批、盘点等分别拆成独立服务。隔离性强，但会引入过多部署单元、跨服务调用和分布式事务，不适合本项目规模。

选择方案 A，因为它在架构表达、开发可控性、数据隔离和答辩展示之间平衡最好。

## 组件边界

| 组件 | 输入 | 输出/职责 |
|------|------|-----------|
| Vue 前端 | 用户操作、JWT、分页参数 | 页面、表单、表格、检测框预览、审批和统计 |
| gateway-service | HTTP 请求 | 路由、traceId、CORS、JWT 初步校验、统一错误格式 |
| identity-service | 登录信息、用户/角色变更 | JWT、权限和组织 API；写入 `identity_db` |
| archive-service | 员工/材料/文件/OCR 请求 | 员工档案、版本、对象存储和 OCR 业务绑定；写入 `archive_db` |
| workflow-service | 流程和申请命令 | 人事申请、审批、使用、归还、盘点和异常；写入 `workflow_db` |
| ocr-service | PNG/JPEG 图片、OCR 查询/修订 | 文本块、字段、置信度、检测框效果图和 OCR 任务状态 |
| MySQL primary/replica | 服务 SQL | 主库写入、只读副本查询和异步复制 |
| Redis | 缓存键、幂等键、短时锁 | 会话/权限/字典/摘要缓存和重复请求控制 |
| MinIO | 文件对象 | 原图、检测框 PNG、导出文件的私有存储 |

## 读写分离不变量

1. 业务事实只以对应服务的 MySQL 主库为准。
2. 写操作永远不能路由到只读副本。
3. 事务中执行的读操作必须满足 `readOnly=false` 时主库，`readOnly=true` 时副本的约定。
4. 写入后的确认读取和同请求读己之写操作强制主库。
5. 副本不可用时普通查询可以降级主库，但必须记录指标；不能因为副本故障回写缓存错误值。
6. 服务之间只传业务 ID 和 API 响应，不共享数据库连接和 Mapper。

## 核心数据库模型

### `identity_db`

`sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`org_department`、`org_position`、`audit_log`。

用户以唯一 `username` 登录；角色和权限使用编码；部门通过 `parent_id` 构成树；身份库中的 `employee_id` 只指向 archive-service 的逻辑 ID。

### `archive_db`

`employee`、`archive_record`、`archive_document`、`file_object`、`archive_version`、`ocr_binding`、`ocr_field_confirmation`、`audit_log`。

档案材料通过 `archive_record -> archive_document -> archive_version` 形成不可覆盖的版本链。`file_object` 只保存对象存储元数据。`ocr_binding` 记录 `ocr_task_id`、任务状态、检测框地址/哈希、确认和发布状态；完整 OCR 证据留在 ocr-service。

### `workflow_db`

`hr_request`、`approval_record`、`archive_access_application`、`archive_access_item`、`archive_use_record`、`inventory_task`、`inventory_item`、`audit_log`。

流程表的扩展字段放在 `payload_json`，但员工、档案、版本和审批人仍使用明确业务 ID。跨库对象在提交和状态迁移时由 archive-service 或 identity-service API 验证。

## OCR 集成协议

档案服务负责业务编排，OCR 服务负责识别证据。调用顺序如下：

1. 档案服务验证当前用户权限、文件 MIME、大小、实际图片内容和 SHA-256。
2. 文件写入 MinIO，创建 `file_object`、`archive_document` 和 `archive_version`。
3. 档案服务以 multipart 调用 `POST /api/ocr/tasks`，携带材料类型。
4. OCR 同步返回 `taskId`、任务状态、文本块、字段、置信度、检测框 URL 和引擎版本。
5. 档案服务在主库写入 `ocr_binding` 和字段确认初始值，返回聚合结果。
6. 页面通过档案服务代理读取检测框预览；OCR 返回 `DETECTION_PREVIEW_NOT_FOUND` 时，页面保留识别结果并提示预览证据缺失。
7. 人事人员修订字段时，档案服务先鉴权，再调用 OCR 修订接口，并在 `ocr_field_confirmation` 保存业务确认记录。
8. 发布前必须校验档案版本、确认人和当前状态，发布后写入审计日志并使相关列表缓存失效。

## 错误和恢复

- 文件校验失败：不创建 OCR 任务，返回 `FILE_TYPE_NOT_SUPPORTED` 或 `INVALID_IMAGE`。
- OCR 超时：档案版本保留，`ocr_binding` 标记 `FAILED`，原图可人工录入；对同一版本使用幂等键避免重复任务。
- 检测框不存在：返回 `DETECTION_PREVIEW_NOT_FOUND`，不把 OCR 任务改为失败。
- 主库事务失败：不写入成功缓存，不返回发布成功；对象存储中的孤儿文件由后续清理任务根据引用关系处理。
- Redis 不可用：登录会话和权限按系统策略拒绝或短时降级，业务事实查询直接访问数据库；不得把 Redis 故障转换成数据删除。
- 审批重复提交：按 `Idempotency-Key` 或业务单号返回第一次处理结果，状态机拒绝非法重复迁移。

## 接口最小契约

业务接口统一使用 `code`、`message`、`data`、`traceId`。内部 OCR 接口保留现有路径和字段命名，不由 Java 服务重写文本块坐标。

```text
POST /api/auth/login
GET  /api/auth/me
GET  /api/employees
POST /api/employees
POST /api/archive/employees/{employeeId}/documents
POST /api/archive/versions/{versionId}/ocr
GET  /api/archive/versions/{versionId}/ocr-result
GET  /api/archive/ocr-bindings/{bindingId}/detection-preview
PUT  /api/archive/ocr-bindings/{bindingId}/fields/{fieldCode}
POST /api/hr-requests
POST /api/hr-requests/{id}/submit
POST /api/approvals/{id}/approve
POST /api/archive-access-applications
POST /api/archive-uses/{id}/return
POST /api/inventory-tasks
```

## 测试门槛

- 单元测试：状态机、权限范围、字段确认、读写路由、缓存 key 和错误码。
- 集成测试：MyBatis SQL、主库写入/副本查询、Redis 缓存失效、MinIO 文件元数据和 OCR HTTP Mock。
- 接口测试：登录、档案上传、OCR 结果、检测框下载、字段修订、申请审批、归还异常。
- 端到端测试：从新建员工到 OCR 版本发布，再到档案申请、审批、使用和归还的完整链路。
- 回归测试：已有 `ocr-service/tests/` 必须持续通过；业务系统不应改变 OCR 的原有字段、坐标和错误码契约。

## 实施前审核项

审核时请重点确认：

1. 四个 Java 服务加一个 OCR 服务的边界是否符合“核心功能拆开但不拆分太细”。
2. 三个业务库共享一组 MySQL 主从集群，是否符合读写分离要求。
3. `employee` 由 archive-service 维护、`employee_id` 在 identity/workflow 中使用逻辑引用的归属是否明确。
4. OCR 原始证据留在现有 OCR 服务、业务库保存绑定和确认结果的方式是否满足追溯需求。
5. 首期是否先按“身份与组织、档案 OCR 闭环、基础审批/使用”顺序进入实施计划。

本文件审核通过后，下一步再为基础设施、身份服务、档案 OCR 闭环和流程服务分别编写实施计划；在此之前不创建业务工程代码、DDL 或前端页面。
