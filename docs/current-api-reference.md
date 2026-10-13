# 当前 API 参考

本文按当前 Spring Boot 控制器整理。请求与响应 DTO、参数校验和状态机以 `server/src/main/java/com/hrplatform` 为准；路由调整时应同步更新本文。

## 调用约定

- 平台 API 根路径为 `/api`。除登录和注册外，调用者通过 `Authorization: Bearer <JWT>` 传递登录令牌。
- 平台 JSON 接口使用统一响应结构 `code`、`message`、`data`。文件下载和 OCR 预览接口直接返回二进制内容。
- JSON 响应体和 `X-Trace-Id` 响应头使用同一个 traceId。服务端接受 1-128 位字母、数字、点、下划线和连字符；缺失、占位或不安全的请求 ID 会替换为 UUID。traceId 可用于关联应用日志与操作日志。
- 服务端根据角色和数据范围进行授权。员工仅访问本人数据，部门负责人访问本部门数据，人事管理员处理企业范围内的人事业务；系统管理员管理员工账号和组织配置，可读取聚合统计及操作审计日志，但不因此获得档案或流程明细权限。
- 列表接口根据对应控制器接受筛选与分页参数；业务状态变更由服务端状态机校验，并发更新通过版本号检查。
- 浏览器只访问 Spring Boot 平台接口。独立 OCR 服务的 `/api/ocr/*` 路由只供受控内部调用，要求 `X-OCR-Internal-Token`；`/health` 为健康检查例外。

## 身份与账号

| 方法 | 路径 | 用途 | 访问角色 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | 用户名和密码换取登录信息 | 公开 |
| `POST` | `/api/auth/register` | 将唯一匹配的在职员工档案绑定为员工账号 | 公开；创建后仅获员工角色 |
| `GET` | `/api/auth/me` | 查询当前登录用户、角色和应用端信息 | 已登录 |
| `GET` | `/api/admin/users` | 分页查询账号 | 系统管理员 |
| `GET` | `/api/admin/users/roles` | 查询可分配角色 | 系统管理员 |
| `POST` | `/api/admin/users` | 创建账号 | 系统管理员 |
| `PUT` | `/api/admin/users/{id}` | 更新账号角色或启用状态 | 系统管理员 |

员工档案的员工编号由平台生成人事主数据。公开注册不允许客户端指定员工编号、部门或角色。

## 组织、员工与档案

| 方法 | 路径 | 用途 | 访问角色 |
| --- | --- | --- | --- |
| `GET` | `/api/departments/tree` | 查询组织树 | 员工、部门负责人、人事管理员、系统管理员 |
| `GET` | `/api/departments/{departmentId}/positions` | 查询部门岗位选项 | 已登录且具备业务端角色 |
| `GET` | `/api/departments/admin` | 查询组织管理数据 | 人事管理员、系统管理员 |
| `GET` | `/api/departments/{departmentId}/positions/admin` | 查询组织维护用岗位 | 人事管理员、系统管理员 |
| `POST` / `PUT` | `/api/departments`、`/api/departments/{departmentId}` | 创建或更新部门 | 人事管理员、系统管理员 |
| `POST` / `PUT` | `/api/departments/{departmentId}/positions`、`/api/departments/{departmentId}/positions/{positionId}` | 创建或更新岗位 | 人事管理员、系统管理员 |
| `POST` | `/api/employees` | 创建员工及档案主记录 | 人事管理员 |
| `GET` | `/api/employees` | 按权限范围查询员工列表 | 员工、部门负责人、人事管理员 |
| `GET` | `/api/employees/{employeeId}` | 查询员工详情 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `GET` | `/api/employees/{employeeId}/status-history` | 分页查询员工状态、部门和岗位变更历史，并关联来源申请与操作人 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `PUT` | `/api/employees/{employeeId}/profile` | 更新员工必要档案字段 | 人事管理员 |
| `POST` | `/api/archive/employees/{employeeId}/documents` | 上传档案文件并创建版本 | 人事管理员 |
| `GET` | `/api/archive/employees/{employeeId}/documents` | 查询员工档案目录 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `GET` | `/api/archive/documents/{documentId}/versions` | 查询档案版本记录 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `GET` | `/api/archive/versions/{versionId}/download` | 下载原始文件 | 员工、部门负责人、人事管理员；受数据范围和有效内容读取授权约束 |

## OCR 识别与复核

以下接口由平台接收登录态并校验数据范围。平台服务端使用内部令牌访问独立 OCR 服务。

| 方法 | 路径 | 用途 | 访问角色 |
| --- | --- | --- | --- |
| `POST` | `/api/archive/versions/{versionId}/ocr` | 对档案版本启动或重试 OCR | 人事管理员；须具备版本级内容读取授权 |
| `GET` | `/api/archive/versions/{versionId}/ocr-result` | 查询任务状态、文本块、字段和修订记录 | 员工、部门负责人、人事管理员；受数据范围和版本级内容授权约束 |
| `GET` | `/api/archive/ocr-bindings/{bindingId}/preview` | 查看检测框预览；兼容路径 `/detection-preview` | 员工、部门负责人、人事管理员；受数据范围和版本级内容授权约束 |
| `PUT` | `/api/archive/ocr-bindings/{bindingId}/fields/{fieldCode}` | 修订映射字段并记录原因和操作人 | 人事管理员；须具备版本级内容读取授权 |

识别失败、结果暂不可用、低置信度和格式校验失败是不同状态，调用方应按响应中的状态及错误信息显示，不能将空字段视为识别通过。原图与字段修订记录分别保留。

## 人事流程与待办

| 方法 | 路径 | 用途 | 访问角色 |
| --- | --- | --- | --- |
| `POST` | `/api/hr-requests` | 创建入职、调动、请假或离职申请 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `GET` | `/api/hr-requests` | 查询申请列表 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `GET` | `/api/hr-requests/{id}` | 查询申请明细和审批时间线 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `POST` | `/api/hr-requests/{id}/submit` | 提交草稿 | 申请人 |
| `POST` | `/api/hr-requests/{id}/cancel` | 撤回允许撤回的申请 | 申请人 |
| `POST` | `/api/hr-requests/{id}/approve` | 审批当前节点 | 当前节点对应审批角色 |
| `POST` | `/api/hr-requests/{id}/reject` | 驳回申请并写入意见 | 当前节点对应审批角色 |
| `PUT` | `/api/hr-requests/{id}/handover` | 确认离职交接完成 | 人事管理员 |
| `GET` | `/api/todos` | 查询当前用户可处理或关注的待办 | 已登录；按角色、数据范围和业务状态过滤 |

入职、调动和离职在批准后按生效日更新员工主数据；请假审批不改变任职状态。审批和执行前都会检查员工当前状态，避免将不适用的流程覆盖到员工记录。

创建接口根据 `requestType` 解析为入职、调动、请假或离职专属请求体。各类型只能提交自身字段；不支持的字段（包括 `payloadJson`）会被拒绝并返回 `400 VALIDATION_ERROR`。`sourceVersionId` 可选；提交时必须指向数据范围内且属于所选员工的档案版本。驳回请求必须提供非空 `comment`，服务端修剪首尾空格并限制为 512 字符；通过请求的意见仍可选。

## 档案访问、盘点与合同

| 方法 | 路径 | 用途 | 访问角色 |
| --- | --- | --- | --- |
| `POST` | `/api/archive-access` | 创建档案访问或借用申请 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `GET` | `/api/archive-access` | 查询档案访问申请 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `GET` | `/api/archive-access/{id}` | 查询申请、档案版本、使用和异常处理历史 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `POST` | `/api/archive-access/{id}/submit` | 提交访问申请 | 申请人 |
| `POST` | `/api/archive-access/{id}/approve` | 批准档案访问申请 | 人事管理员 |
| `POST` | `/api/archive-access/{id}/reject` | 驳回待授权申请并保存必填原因 | 人事管理员 |
| `POST` | `/api/archive-access/{id}/use` | 登记开始使用 | 获批申请人 |
| `POST` | `/api/archive-access/{id}/return` | 登记正常或异常归还、移交 | 当前使用人 |
| `POST` | `/api/archive-access/{id}/resolve` | 登记异常处理并关闭异常 | 人事管理员 |
| `POST` / `GET` | `/api/inventory-tasks` | 创建盘点任务 / 查询任务 | 部门负责人、人事管理员 |
| `POST` | `/api/inventory-tasks/{id}/start` | 开始盘点 | 部门负责人、人事管理员 |
| `GET` | `/api/inventory-tasks/{id}/items` | 查询盘点项 | 部门负责人、人事管理员；受数据范围约束 |
| `GET` | `/api/inventory-tasks/{id}/items/{itemId}/versions` | 查询实际版本选项 | 部门负责人、人事管理员 |
| `POST` | `/api/inventory-tasks/{id}/items/{itemId}/check` | 登记核对结果 | 部门负责人、人事管理员 |
| `POST` | `/api/inventory-tasks/{id}/items/{itemId}/resolve` | 处理盘点差异 | 部门负责人、人事管理员 |
| `POST` | `/api/inventory-tasks/{id}/complete` | 完成盘点；仍有未解决异常时拒绝 | 部门负责人、人事管理员 |
| `GET` | `/api/contracts` | 查询合同 | 员工、部门负责人、人事管理员；受数据范围约束 |
| `POST` | `/api/contracts` | 登记合同并关联员工和来源档案版本 | 人事管理员 |
| `PUT` | `/api/contracts/{id}/renewal-status` | 登记续签或不续签 | 人事管理员 |

## 统计与审计

| 方法 | 路径 | 用途 | 访问角色 |
| --- | --- | --- | --- |
| `GET` | `/api/statistics/overview` | 查询聚合统计 | 已登录角色；按授权范围返回汇总 |
| `GET` | `/api/statistics/report` | 查询月度趋势、部门聚合和已完成流程处理周期 | 已登录角色；按授权范围返回聚合值 |
| `GET` | `/api/operation-logs` | 按条件分页查询操作日志，包含变更前后摘要 | 人事管理员、系统管理员 |

## OCR 服务内部 API

OCR 服务默认监听 `127.0.0.1:8000`。除健康检查外，所有 `/api/ocr/*` 路由必须携带 `X-OCR-Internal-Token`。路由包括任务上传、分页查询、按 ID 读取任务、检测预览、字段修订、修订历史、运行统计和清晰度评测。字段与响应示例见 [OCR 服务说明](../ocr-service/README.md)。
