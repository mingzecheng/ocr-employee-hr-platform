# 微服务、数据层与 OCR 契约设计

> 日期：2026-09-20
> 状态：待用户审核
> 适用范围：当前平台后续开发与验收

## 1. 设计目标

在现有工程基础上继续开发，不重建项目。平台采用核心业务微服务架构，服务不做过度拆分；业务数据使用 MySQL 主从读写分离，Redis 负责可重建缓存、幂等和短时锁；后端使用 Spring Boot + MyBatis，前端使用 Vue 3；OCR 保持独立的 Python FastAPI + PaddleOCR 服务。

本设计优先保证员工档案、OCR 识别和人事流程的可验收闭环。OCR 的核心结果包括原图、文本块、置信度、原图坐标、字段结果和检测框效果图。OCR 原始证据由 OCR 服务负责保存，业务服务只保存业务关联和摘要。

## 2. 服务边界

```text
Vue 3
  -> gateway-service
       -> identity-service
       -> archive-service -> ocr-service
       -> workflow-service -> archive-service 内部授权接口

identity_db   archive_db   workflow_db
       \          |          /
          MySQL primary -> replica
                    |
                  Redis
```

| 服务 | 事实数据与职责 | 不负责的内容 |
|---|---|---|
| `gateway-service` | 统一入口、路由、traceId | 不持有业务表、不直接访问数据库 |
| `identity-service` | 用户、角色、权限、部门、岗位、登录 | 不读取档案库和流程库 |
| `archive-service` | 员工、档案材料、文件版本、OCR 业务绑定、对象存储访问 | 不保存 OCR 服务的完整原始任务 JSON |
| `workflow-service` | 入职/调动/离职、档案访问、领取归还、盘点、审批 | 不跨库读取 identity/archive 表 |
| `ocr-service` | 图片识别、原图、OCR 任务、文本块、字段、检测框 PNG、字段修订 | 不理解员工权限和流程状态 |

服务之间只传业务 ID 和受控 HTTP 请求，不建立跨库物理外键。workflow-service 在写入或读取档案相关流程前，通过 archive-service 内部授权接口校验资源归属；客户端传入的部门 ID 不作为授权依据。

## 3. 数据与存储设计

### 3.1 identity_db

由 identity-service 独占，包含 `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`org_department`、`org_position` 和身份审计日志。用户保留 `employee_id`、`department_id` 逻辑关联，用于生成 JWT 数据范围。

### 3.2 archive_db

由 archive-service 独占，包含 `employee`、`archive_record`、`archive_document`、`file_object`、`archive_version`、`ocr_binding`、`ocr_field_confirmation` 和档案审计日志。

`ocr_binding` 只保存 `version_id`、`ocr_task_id`、任务状态、引擎版本、字段数量和检测框预览的 URL、媒体类型、大小、哈希、宽高等摘要。原图和检测框 PNG 由 MinIO 保存，完整 OCR JSON 与字段修订由 OCR SQLite 保存，不进入 MySQL 或 Redis。

### 3.3 workflow_db

由 workflow-service 独占，包含 `hr_request`、`approval_record`、`archive_access_application`、`archive_access_item`、`archive_access_approval`、`archive_use_record`、`inventory_task`、`inventory_item` 和 `inventory_resolution`。

流程库中的 `employee_id`、`archive_document_id`、`archive_version_id` 是逻辑引用。`target_department_id` 以及盘点明细中的员工/部门字段是创建时的范围快照，用于稳定查询和审计，不替代实时授权校验。

### 3.4 OCR 本地持久化

- SQLite：保存 OCR 任务 JSON、字段修订历史和内部对象引用列。
- MinIO：保存 `archive/...` 档案原图、`ocr/source/...` 独立 OCR 原图和 `ocr/preview/{taskId}.png` 检测框 PNG。
- 识别期间原图只下载到系统临时目录；识别失败仍保留 MinIO 原图和失败信息。
- `ocr-service/data` 与 `ocr-service/app/data` 仅作为历史迁移输入，不能继续写入图片。
- 成功任务缺少预览时，首次调用检测框接口根据原图和文本块补生成；原图或文本块不存在时返回 `DETECTION_PREVIEW_NOT_FOUND`。

## 4. 主从读写路由

- 写入、状态迁移、审批、版本创建、OCR 绑定和写后确认查询使用 MySQL primary。
- 普通列表、历史详情和统计查询使用 MySQL replica。
- 写事务内、刚写入后的读己之写请求强制使用 primary。
- 副本不可用或延迟超过阈值时，读请求可降级到 primary 并记录日志；写请求不切换到 replica。
- 每个服务通过 `MYSQL_WRITE_URL`、`MYSQL_READ_URL` 配置自己的数据源，业务 Mapper 不直接感知连接地址。
- 服务内事务使用 `@Transactional`；服务间不引入分布式事务，失败通过状态记录和重试/补偿处理。

## 5. Redis 约定

| Key | 内容 | TTL / 失效 |
|---|---|---|
| `hr:perm:user:{userId}` | 权限和数据范围摘要 | 30 分钟；角色或权限变更主动删除 |
| `hr:org:department:tree` | 部门树 | 10 分钟；部门变更主动删除 |
| `hr:list:archive:{sha256}` | 脱敏员工列表 | 60 秒；员工或档案提交事务后清理前缀 |
| `hr:todo:user:{userId}:{sha256(scopeKey)}` | 当前用户授权范围内的可重建待办摘要 | 30 秒；按用户和 scope 隔离，过期后回源 |
| `hr:idempotent:{requestId}` | 请求幂等占用标记 | 默认 10 分钟；必须有 TTL |
| `hr:lock:{resourceType}:{resourceId}` | 版本发布、归还等短时锁 | 不超过 30 秒；必须有 TTL |

Redis 只做加速和并发控制，不保存原图、文件二进制、完整 OCR JSON、检测框 PNG 或不可重建业务事实。缓存不可用时，业务写入不能失败；查询应回源数据库并记录降级日志。

## 6. HTTP 接口契约

### 6.1 统一响应

Java 业务服务和网关接口统一使用：

```json
{
  "code": "0",
  "message": "OK",
  "data": {},
  "traceId": "..."
}
```

成功码为字符串 `"0"`。错误响应的 `data` 为 `null`。文件下载和检测框预览为二进制响应，不使用 JSON 包装，但失败时返回统一错误 JSON。

### 6.2 对外业务接口

| 服务 | 方法与路径 | 当前用途 |
|---|---|---|
| identity | `POST /api/auth/login` | 登录并返回 JWT |
| identity | `GET /api/auth/me` | 当前用户、角色和数据范围 |
| identity | `GET /api/departments/tree` | 部门树查询 |
| archive | `GET /api/employees?page=&pageSize=` | 数据范围内员工列表 |
| archive | `POST /api/employees` | 创建员工 |
| archive | `POST /api/archive/employees/{employeeId}/documents` | 上传档案材料并创建版本 |
| archive | `GET /api/archive/employees/{employeeId}/documents` | 材料列表 |
| archive | `GET /api/archive/documents/{documentId}/versions` | 版本历史 |
| archive | `GET /api/archive/versions/{versionId}/download` | 受控原图读取 |
| archive | `POST /api/archive/versions/{versionId}/ocr` | 创建或复用 OCR 绑定 |
| archive | `GET /api/archive/versions/{versionId}/ocr-result` | 查询 OCR 聚合结果 |
| archive | `GET /api/archive/ocr-bindings/{bindingId}/detection-preview` | 代理检测框 PNG |
| archive | `PUT /api/archive/ocr-bindings/{bindingId}/fields/{fieldCode}` | 字段修订 |
| workflow | `POST /api/hr-requests` | 创建人事申请 |
| workflow | `POST /api/hr-requests/{id}/submit` | 提交流程 |
| workflow | `POST /api/approvals/{id}/approve` / `reject` | 审批处理 |
| workflow | `POST /api/archive-access-applications` | 创建档案访问/借用申请 |
| workflow | `POST /api/archive-access-applications/{id}/checkout` | 登记领取 |
| workflow | `POST /api/archive-uses/{id}/return` | 登记归还、移交或异常 |
| workflow | `POST /api/inventory-tasks` | 创建盘点任务 |
| archive | `GET /api/statistics/overview` | 授权范围统计 |
| workflow | `GET /api/todos?limit=1..100` | 当前用户授权范围内的待办摘要 |

待办查询统一返回 `ApiResponse<TodoData>`，其中 `TodoData` 包含 `items`、`total`、`pendingApprovalCount`、`dueSoonCount`、`overdueCount`、`ocrFailedCount` 和 `generatedAt`。每个 `TodoItem` 包含 `id`、`type`、`title`、`resourceId`、`status`、`priority`、`dueAt`、`createdAt` 和 `targetPath`。

`TodoItem.type` 仅允许 `HR_REQUEST_APPROVAL`、`ARCHIVE_ACCESS_APPROVAL`、`ARCHIVE_RETURN_DUE`、`OCR_FAILED`；`TodoItem.priority` 仅允许 `HIGH`、`MEDIUM`、`LOW`。待办缓存值只保存可由业务数据重建的摘要，不保存 OCR 文件、完整 OCR JSON、检测框 PNG、文件二进制、对象存储 key 或令牌；缓存 TTL 固定为 30 秒，key 中的 scope 摘要保证不同数据范围互不污染。

### 6.3 OCR 内部接口

| 方法与路径 | 约定 |
|---|---|
| `POST /api/ocr/tasks` | multipart 上传 PNG/JPEG；同步返回 `taskId`、状态、文本块、字段和检测框摘要 |
| `GET /api/ocr/tasks/{taskId}` | 查询任务、原图元数据、文本块、字段、置信度和错误信息 |
| `GET /api/ocr/tasks/{taskId}/detection-preview` | 返回 `image/png`；任务不存在返回 `TASK_NOT_FOUND`，效果图无法提供返回 `DETECTION_PREVIEW_NOT_FOUND` |
| `PUT /api/ocr/tasks/{taskId}/fields/{fieldCode}` | 更新字段当前确认值，同时保存修订历史；原始文本块不覆盖 |

OCR 文本块至少包含 `text`、`confidence`、`bbox=[left,top,right,bottom]` 和 `pageNo`；字段至少包含字段编码、值、置信度、坐标、校验状态和 `reviewRequired`。检测框效果图必须包含原图内容、检测框、序号、置信度和识别文本。

### 6.4 错误码与超时

业务错误至少包括 `AUTH_REQUIRED`、`FORBIDDEN`、`VALIDATION_ERROR`、`RESOURCE_NOT_FOUND`、`STATE_NOT_ALLOWED`、`DUPLICATE_REQUEST`、`OCR_FAILED`、`DETECTION_PREVIEW_NOT_FOUND` 和 `SERVICE_UNAVAILABLE`。archive-service 调用 OCR 必须设置连接超时和读取超时；OCR 失败不得删除已保存原图。

## 7. 状态与幂等

- OCR：`RECOGNIZING -> SUCCEEDED/FAILED`，结果保存后再返回。
- 档案访问：`DRAFT -> PENDING_DEPT_APPROVAL -> PENDING_HR_APPROVAL -> APPROVED -> IN_USE -> RETURNED/ABNORMAL`。
- 人事流程：`DRAFT -> SUBMITTED -> PENDING_DEPT_APPROVAL -> PENDING_HR_APPROVAL -> APPROVED/REJECTED`。
- 同一档案版本重复触发 OCR 时复用现有绑定和 `taskId`。
- 写入接口使用请求号或幂等键；重复请求返回既有业务结果，不重复创建材料、申请或审批记录。
- 每次状态迁移在本服务主库事务内同时写入审计记录。

## 8. 测试与审核门槛

实现前必须审核本规格和对应实施计划。实现阶段按以下证据验收：

1. 数据库迁移可重复启动，主库写入和副本查询路由可测试。
2. Redis 命中、过期、主动失效、幂等占用和缓存不可用降级有测试。
3. OCR API 覆盖有效图片、无效图片、识别失败、完整文本块、坐标、检测框 PNG、历史预览补生成及两类 404 错误。
4. archive-service 覆盖 OCR 绑定幂等、字段修订、预览代理、超时和越权。
5. workflow-service 覆盖状态机、跨服务授权、部门数据范围和异常归还。
6. 前端只通过网关访问业务接口，并覆盖登录、上传、OCR 结果、检测框预览和失败状态。

## 9. 当前实现与后续范围

当前已实现并通过验收：OCR 核心识别、原图保存、文本检测框 PNG、历史预览补生成、archive-service OCR 对接、身份/档案/workflow 核心服务、主从连接配置、Redis 员工列表缓存、数据范围、流程访问闭环、Vue OCR 工作台和端到端验收脚本。

后续按 P1 顺序处理：待办缓存、合同管理、批量导入/导出以及更丰富的统计维度。低清、严重倾斜、密集表格和手写图片仍需通过专项样本评测和参数调优，不把检测框效果图误认为识别准确率证明。
