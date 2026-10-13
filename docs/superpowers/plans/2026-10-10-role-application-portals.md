# 角色应用端重设实施计划

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按员工、部门负责人、人事管理员和系统管理员四种用例角色提供四个独立应用端，并使页面入口与服务端授权边界一致。

**Architecture:** 在现有 Vue/Vite 仓库添加独立 HTML 入口与每端独立 Hash 路由，复用 API、认证 store 和共享业务页面。系统管理员默认从 HR 与部门审批业务权限中分离；多角色账号只通过显式分配角色获得多个入口。

**Tech Stack:** Vue 3、TypeScript、Vue Router、Pinia、Element Plus、Vite、Spring Boot、Spring Security。

## 全局约束

- 不引入新的运行时依赖。
- 根入口仅用于登录与工作台选择，四个业务端使用独立入口、路由表和布局。
- 服务端权限与数据范围是最终授权依据，隐藏菜单不能替代 `@PreAuthorize`。
- `SYSTEM_ADMIN` 不默认获得 HR/OCR/部门审批能力。
- 登录页不得显示演示账号或密码。
- 保留现有 API 业务流程和 OCR/MinIO 契约。

### Task 1：更新设计基线

**Files:**
- Create: `docs/superpowers/specs/2026-10-10-role-application-portals-design.md`
- Create: `docs/superpowers/plans/2026-10-10-role-application-portals.md`
- Modify: `docs/use-case-analysis.md`
- Modify: `docs/diagrams/employee-hr-platform-use-case.drawio`
- Modify: `docs/diagrams/employee-hr-platform-use-case.drawio.png`

- [x] 审核当前路由、角色守卫、页面入口和后端角色声明。
- [x] 定义四类应用端、入口和多角色切换规则。
- [x] 将用例矩阵和系统设计文档同步到新角色边界；用例图待随新权限关系更新。

### Task 2：建立四个前端应用入口

**Files:**
- Modify: `web/vite.config.ts`
- Modify: `web/index.html`
- Modify: `web/src/main.ts`
- Create: `web/employee.html`
- Create: `web/manager.html`
- Create: `web/hr.html`
- Create: `web/admin.html`
- Create: `web/src/portal/portal-config.ts`
- Create: `web/src/portal/create-portal-app.ts`
- Create: `web/src/portal/employee-entry.ts`
- Create: `web/src/portal/manager-entry.ts`
- Create: `web/src/portal/hr-entry.ts`
- Create: `web/src/portal/admin-entry.ts`

- [x] 配置 Vite 多页面输入，根入口只注册登录和角色工作台选择。
- [x] 为四个 HTML 入口创建独立 Vue 应用和 Hash 路由。
- [x] 为每个 portal 路由显式列出角色允许表；未授权访问显示无权访问页。

### Task 3：构建角色工作台和首页

**Files:**
- Create: `web/src/views/WorkspaceSelectView.vue`
- Create: `web/src/layouts/PortalLayout.vue`
- Create: `web/src/views/EmployeeHomeView.vue`
- Create: `web/src/views/ManagerHomeView.vue`
- Create: `web/src/views/HrHomeView.vue`
- Create: `web/src/views/AdminHomeView.vue`
- Modify: `web/src/views/LoginView.vue`
- Modify: `web/src/router/index.ts`
- Modify: `web/src/styles.css`

- [x] 根入口根据角色数量直达唯一 portal 或展示已授权工作台列表。
- [x] 多角色 portal 提供工作台切换，选择器只根据已登录角色生成入口。
- [x] 员工首页展示本人待办、申请状态和档案访问；部门首页突出本部门审批与盘点异常。
- [x] HR 首页突出 HR 审批、OCR 复核、离职交接、授权和合同到期；系统首页突出账号、角色、组织与审计。
- [x] 删除登录页演示账号回填控件及演示账号的默认跳转逻辑。
- [x] 提供 390px 移动端布局和桌面端 HR/系统管理页面。

### Task 4：按用例边界调整后端授权

**Files:**
- Modify: `server/src/main/java/com/hrplatform/employee/EmployeeController.java`
- Modify: `server/src/main/java/com/hrplatform/archive/ArchiveController.java`
- Modify: `server/src/main/java/com/hrplatform/ocr/OcrController.java`
- Modify: `server/src/main/java/com/hrplatform/workflow/RequestController.java`
- Modify: `server/src/main/java/com/hrplatform/workflow/RequestStateMachine.java`
- Modify: `server/src/main/java/com/hrplatform/access/ArchiveAccessController.java`
- Modify: `server/src/main/java/com/hrplatform/inventory/InventoryController.java`
- Modify: `server/src/main/java/com/hrplatform/contract/ContractController.java`
- Modify: `server/src/main/java/com/hrplatform/statistics/StatisticsController.java`
- Modify: `server/src/main/java/com/hrplatform/audit/AuditController.java`

- [x] HR 业务操作只由 HR 角色执行；部门审批只由本部门负责人执行。
- [x] 系统管理员保留账号、组织配置、聚合统计和审计读取权限，不默认获取员工档案、OCR 修订、业务审批、授权或盘点权限。
- [x] 多角色账号仍以角色集合并集获得能力，系统管理员身份本身不提升业务权限。
- [x] 通过服务端权限声明和数据范围源码检查确认入口与 API 的角色边界一致。

### Task 5：同步文档并验证交付

**Files:**
- Modify: `docs/use-case-analysis.md`
- Modify: `docs/diagrams/employee-hr-platform-use-case.drawio`
- Modify: `docs/diagrams/employee-hr-platform-use-case.drawio.png`
- Modify: `docs/superpowers/specs/2026-10-01-employee-hr-platform-rebuild-design.md`

- [x] 更新角色矩阵、应用端信息架构、权限边界和用例关系图。
- [x] 在浏览器中检查四个入口、角色首页、工作台切换和越权路由状态。
- [x] 执行前端生产构建、XML/重复 ID 检查、`git diff --check`。
- [x] 记录前端、后端和 OCR 服务的真实运行状态。

### 浏览器验收记录

- 四个角色入口的首页均已在浏览器检查；单角色账号只能进入所属入口，多角色选择器按已授权角色显示工作台。
- 已检查系统管理员访问员工业务、部门负责人访问组织管理、员工访问系统管理等越权路由，均落入无权访问页；服务端接口也以 403 验证系统管理员不能读取员工明细。
- 本轮收尾重新核对了 HR 首页待办、OCR 质量、合同和授权指标区域；最终重新构建前端并运行 `git diff --check`。
- 演示账号均为单角色，因此多角色工作台切换按路由守卫和角色集合逻辑进行源码核对，未通过演示账号实测多角色组合。
- 最终前端生产构建成功，五个 HTML 入口均输出；两个 Draw.io 文件均可解析且节点 ID 唯一。构建提示一个共享 chunk 超过 500 KB。
- 本地前端在 `127.0.0.1:5173` 提供服务，浏览器最终已退出 HR 演示账号并回到无样例账号的登录页；后端工作台请求曾成功返回 HR 数据。
- PaddleOCR OCR 服务未能完成启动：进程停留在 PaddleOCR 依赖导入/应用初始化阶段约五分钟，`127.0.0.1:8000/health` 始终拒绝连接；挂起进程已停止，未切换为 mock 引擎。
