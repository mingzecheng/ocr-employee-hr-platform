# 04 · 前端视图事实清单

> 来源：`web/src/views/*.vue`、`web/src/portal/create-portal-router.ts`、`web/src/portal/portal-config.ts`、`web/src/api/http.ts`、`web/vite.config.ts`。
> 技术栈：Vue 3 + TypeScript + Vite + Element Plus + Pinia + vue-router（hash 模式）。

---

## 1. 多入口架构

Vite 构建配置（`vite.config.ts`）声明 5 个 HTML 入口：

| 入口 HTML | 入口 TS | 用途 | 允许角色 |
|---|---|---|---|
| `web/index.html` | `web/src/main.ts` → `web/src/router/index.ts` | 公共入口：登录/注册/选择工作台 | 公开 |
| `web/employee.html` | `web/src/portal/employee-entry.ts` → `mountPortal('employee')` | 员工自助服务端 | `EMPLOYEE` |
| `web/manager.html` | `web/src/portal/manager-entry.ts` → `mountPortal('manager')` | 部门工作台 | `DEPT_MANAGER` |
| `web/hr.html` | `web/src/portal/hr-entry.ts` → `mountPortal('hr')` | 人事运营工作台 | `HR_ADMIN` |
| `web/admin.html` | `web/src/portal/admin-entry.ts` → `mountPortal('admin')` | 系统管理控制台 | `SYSTEM_ADMIN` |

公共路由（`router/index.ts`）：
- `/login` → `LoginView.vue`（公开，标题"登录"）
- `/register` → `RegisterView.vue`（公开，标题"员工注册"）
- `/workspaces` → `WorkspaceSelectView.vue`（登录后选工作台）
- `/` → redirect `/workspaces`

未登录访问任意门户时，`create-portal-router` 守卫会把当前 URL 存入 `sessionStorage['hr.portalReturnTo']` 并跳转 `/#/login`，登录后按 `authorizedReturnUrl` 回跳。

---

## 2. 四端路由表（`create-portal-router.ts`）

`commonRoutes(roles, ocrRoles)` 在 employee/manager/hr 三端共享，admin 端独立。

### 2.1 员工端 employee.html（允许角色：`EMPLOYEE`）
| 路径 | 组件 | 标题 |
|---|---|---|
| `/` | `EmployeeHomeView.vue` | 个人工作台 |
| `/todos` | `TodoView.vue` | 待办中心 |
| `/todos/:kind/:id` | `TodoDetailView.vue` | 待办详情 |
| `/employees` | `EmployeeListView.vue` | 员工与档案（本人视角） |
| `/employees/:id/archive` | `EmployeeArchiveView.vue` | 档案详情 |
| `/workflows` | `WorkflowView.vue` | 人事流程 |
| `/contracts` | `ContractsView.vue` | 合同台账 |
| `/archive-access` | `ArchiveAccessView.vue` | 档案访问 |
| `/todos/ocr/:versionId`、`/ocr/:versionId` | `OcrReviewView.vue` | OCR 识别结果（ocrRoles=EMPLOYEE） |

### 2.2 部门负责人端 manager.html（允许角色：`DEPT_MANAGER`）
| 路径 | 组件 | 标题 |
|---|---|---|
| `/` | `ManagerHomeView.vue` | 团队概览 |
| `/todos`、`/todos/:kind/:id` | `TodoView` / `TodoDetailView` | 审批与待办 |
| `/employees`、`/employees/:id/archive` | 同上 | 部门员工 / 档案详情 |
| `/workflows` | `WorkflowView` | 人事流程（部门审批节点） |
| `/contracts` | `ContractsView` | 合同信息 |
| `/archive-access` | `ArchiveAccessView` | 档案访问 |
| `/inventory` | `InventoryView.vue` | 部门盘点 |
| `/todos/ocr/:versionId`、`/ocr/:versionId` | `OcrReviewView` | OCR 识别结果（ocrRoles=DEPT_MANAGER） |

### 2.3 人事端 hr.html（允许角色：`HR_ADMIN`）
| 路径 | 组件 | 标题 |
|---|---|---|
| `/` | `HrHomeView.vue` | 人事运营总览 |
| `/todos`、`/todos/:kind/:id` | `TodoView` / `TodoDetailView` | 人事待办 |
| `/employees`、`/employees/:id/archive` | 同上 | 员工与档案 |
| `/workflows` | `WorkflowView` | 人事流程（HR 审批节点） |
| `/contracts` | `ContractsView` | 合同台账 |
| `/archive-access` | `ArchiveAccessView` | 档案授权 |
| `/inventory` | `InventoryView` | 盘点与异常 |
| `/organization` | `OrganizationAdminView.vue` | 组织岗位 |
| `/audit-logs` | `AuditLogView.vue` | 审计日志 |
| `/todos/ocr/:versionId`、`/ocr/:versionId` | `OcrReviewView` | OCR 识别结果（ocrRoles=HR_ADMIN，可触发/修订） |

### 2.4 系统管理员端 admin.html（允许角色：`SYSTEM_ADMIN`）
| 路径 | 组件 | 标题 |
|---|---|---|
| `/` | `AdminHomeView.vue` | 平台管理概览 |
| `/admin/users` | `AdminUsersView.vue` | 账号与角色 |
| `/organization` | `OrganizationAdminView.vue` | 组织结构 |
| `/audit-logs` | `AuditLogView.vue` | 审计日志 |

### 2.5 共用
| 路径 | 组件 | 说明 |
|---|---|---|
| `/forbidden` | `PortalForbiddenView.vue` | 无角色访问兜底 |
| `/:pathMatch(.*)*` | redirect `/forbidden` | |

---

## 3. 各页面主要功能与 API 调用

> 所有 API 调用通过 `web/src/api/http.ts` 的 `request<T>(config)`，baseURL=`/api`（Vite dev proxy 到 `http://127.0.0.1:8080`），Bearer token 从 `sessionStorage['hr.accessToken']` 读取。

### 3.1 公共页
| 视图 | 功能 | 调用 API |
|---|---|---|
| `LoginView.vue` | 账号密码登录 | `POST /auth/login` |
| `RegisterView.vue` | 员工自助注册 | `POST /auth/register` |
| `WorkspaceSelectView.vue` | 登录后按角色列出可用门户 | （纯前端 `availablePortals(roles)`） |

### 3.2 首页/仪表盘
| 视图 | 所属端 | 主要功能 | 调用 API |
|---|---|---|---|
| `EmployeeHomeView.vue` | employee | 个人待办数、我的档案概览 | `GET /statistics/overview`、`GET /todos?limit=6` |
| `ManagerHomeView.vue` | manager | 团队概览、待审批、盘点任务数 | `GET /statistics/overview`、`GET /todos?limit=8`、`GET /inventory-tasks` |
| `HrHomeView.vue` | hr | 人事运营总览（指标卡） | （引用 `DashboardView` 同款统计接口） |
| `AdminHomeView.vue` | admin | 平台概览、用户数、部门树 | `GET /statistics/overview`、`GET /admin/users?pageSize=1`、`GET /departments/tree` |
| `DashboardView.vue` | （被 Hr/Admin 嵌入） | 月度趋势、部门分布、合同到期提醒 | `GET /statistics/overview`、`GET /statistics/report`、`GET /todos`、`GET /contracts?status=ACTIVE&renewalStatus=DUE` |

### 3.3 员工与档案
| 视图 | 主要功能 | 调用 API |
|---|---|---|
| `EmployeeListView.vue` | 员工列表（关键词/部门/状态过滤）；HR 可新建/编辑员工 | `GET /employees`、`GET /departments/tree`、`GET /departments/{id}/positions`、`POST /employees`、`PUT /employees/{id}/profile` |
| `EmployeeArchiveView.vue` | 员工详情 + 档案文档列表 + 版本历史 + 状态变更历史；HR 可上传新材料、触发 OCR | `GET /employees/{id}`、`GET /employees/{id}/status-history`、`GET /archive/employees/{id}/documents`、`GET /archive/documents/{id}/versions`、`POST /archive/employees/{id}/documents`（multipart）、`POST /archive/versions/{id}/ocr`、`GET /archive/versions/{id}/ocr-result` |

### 3.4 人事流程与待办
| 视图 | 主要功能 | 调用 API |
|---|---|---|
| `WorkflowView.vue` | 发起入职/调动/请假/离职申请；按类型/状态筛选；提交/审批/驳回/取消/完成交接 | `GET /hr-requests`、`POST /hr-requests`、`POST /hr-requests/{id}/submit|cancel|approve|reject`、`PUT /hr-requests/{id}/handover`、`GET /employees`、`GET /departments/tree`、`GET /departments/{id}/positions` |
| `TodoView.vue` | 当前用户待办聚合列表（待审批/到期/异常/OCR 失败） | `GET /todos?limit=100` |
| `TodoDetailView.vue` | 待办详情：审批意见录入、同意/驳回/撤回、离职交接、档案借用审批/归还/异常闭环 | `GET /hr-requests/{id}`、`GET /archive-access/{id}`、`POST /hr-requests/{id}/{approve|reject|submit|cancel}`、`PUT /hr-requests/{id}/handover`、`POST /archive-access/{id}/{approve|reject|return|resolve}`、`GET /employees?departmentId=...&status=ACTIVE` |

### 3.5 档案借用与盘点
| 视图 | 主要功能 | 调用 API |
|---|---|---|
| `ArchiveAccessView.vue` | 创建借用申请（选员工→选文档→选版本）、提交/审批/归还/异常闭环 | `POST /archive-access`、`POST /archive-access/{id}/{submit|approve|reject|use|return|resolve}`、`GET /archive-access/{id}`、`GET /employees`、`GET /archive/employees/{id}/documents`、`GET /archive/documents/{id}/versions` |
| `InventoryView.vue` | 创建盘点任务（勾选档案版本）、启动、逐项核对、提交差异、闭环 | `GET/POST /inventory-tasks`、`POST /inventory-tasks/{id}/start|complete`、`GET /inventory-tasks/{id}/items`、`GET /inventory-tasks/{id}/items/{itemId}/versions`、`POST /inventory-tasks/{id}/items/{itemId}/check|resolve`、`GET /employees`、`GET /archive/employees/{id}/documents`、`GET /archive/documents/{id}/versions` |

### 3.6 OCR
| 视图 | 主要功能 | 调用 API |
|---|---|---|
| `OcrReviewView.vue` | 展示 OCR 文本块/字段/置信度/检测框图；HR 可人工修订字段值 | `POST /archive/versions/{id}/ocr`、`GET /archive/versions/{id}/ocr-result`、`PUT /archive/ocr-bindings/{bindingId}/fields/{fieldCode}`、检测框图由 `GET /archive/ocr-bindings/{id}/preview`（image/png） |

### 3.7 合同、组织、审计、系统管理
| 视图 | 主要功能 | 调用 API |
|---|---|---|
| `ContractsView.vue` | 合同台账（按状态/续签状态过滤）；HR 新建合同、标记不续签 | `GET /contracts`、`POST /contracts`、`PUT /contracts/{id}/renewal-status`、`GET /employees` |
| `OrganizationAdminView.vue` | 部门树与岗位的增删改 | `GET /departments/admin`、`GET /departments/{id}/positions/admin`、`POST/PUT /departments[/{id}]`、`POST/PUT /departments/{id}/positions[/{posId}]` |
| `AuditLogView.vue` | 操作日志分页（按 action 过滤） | `GET /operation-logs` |
| `AdminUsersView.vue` | 系统用户管理（列表/新建/编辑角色） | `GET /admin/users`、`GET /admin/users/roles`、`POST /admin/users`、`PUT /admin/users/{id}` |

---

## 4. 导航结构（`portal-config.ts`）

| 端 | 导航分组 | 菜单项 |
|---|---|---|
| employee | 个人服务 | 个人工作台、我的待办、我的档案、申请与进度、档案访问、合同信息 |
| manager | 团队工作 / 档案资源 | 团队概览、审批与待办、部门员工、人事流程、合同信息 / 档案访问、部门盘点 |
| hr | 运营中心 / 人员与档案 / 档案资源 / 系统运营 | 运营总览、人事待办 / 员工与档案、人事流程、合同台账 / 档案授权、盘点与异常 / 组织岗位、审计日志 |
| admin | 平台管理 / 平台配置 / 平台审计 | 管理概览、账号与角色 / 组织结构 / 审计日志 |

---

## 5. 关键前端事实

1. **四端是独立 SPA**：每个 HTML 加载自己的 entry ts，挂载独立 router 与 Pinia store；共用 `views/`、`api/http.ts`、`styles.css`。
2. **角色守卫双层**：路由级 `meta.roles` + 门户级 `portal.allowedRoles`；后端 `@PreAuthorize` 是最终防线。
3. **API 响应包装**：`ApiResponse<T>{code,message,data,traceId}`；`request<T>` 助手在 `code !== '0'` 时抛错并带 `code/traceId`。
4. **401 处理**：axios 响应拦截器在 401 时清 `sessionStorage['hr.accessToken'/'hr.user']`，由路由守卫跳登录。
5. **Vite dev proxy**：`/api` → `http://127.0.0.1:8080`（可被 `HR_API_PROXY_TARGET` 环境变量覆盖）；dev 端口 5173、preview 4173。
6. **没有独立的 OCR 前端页面路由在 admin 端**：admin 端无 OCR 菜单，OCR 复核只能通过 HR 端或员工/部门端的 `/ocr/:versionId` 进入。
7. **`WorkspaceSelectView`** 是登录后落地页，根据 `auth.roles` 展示可进入的门户卡片。
