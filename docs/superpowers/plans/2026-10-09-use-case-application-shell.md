# 用例分析与应用端重设实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline execution is selected for this task). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将企业员工档案与人事流程平台的业务边界整理为可审阅的用例分析和可编辑用例图，并按角色重设现有 Vue 应用端的信息架构。

**Architecture:** 保留现有 Spring Boot API、Vue 3 单应用壳和 Element Plus 组件体系。用例分析以业务流程文档和现有控制器为事实来源；前端仅调整导航分组、角色入口、路由元数据和工作台入口，权限仍由后端接口最终校验。

**Tech Stack:** Vue 3, TypeScript, Vue Router, Pinia, Element Plus, Draw.io mxGraph XML, Vitest, Vite。

## Global Constraints

- 保留此前已完成的登录页、Logo、待办详情、OCR 复核和后端业务流程改动。
- 员工、部门负责人、人事管理员、系统管理员的数据范围和权限边界必须在文档与前端入口中一致表达。
- Draw.io 文件必须使用唯一节点 ID、合法 XML 转义和可编辑的 mxGraph 结构。
- 不引入新的前端依赖，不改动后端接口契约。
- 导航文案使用简体中文，路由元数据必须有明确页面标题。

---

### Task 1: 编写用例分析文档

**Files:**
- Create: `docs/use-case-analysis.md`
- Reference: `docs/business-process-design.md`
- Reference: `server/src/main/java/com/hrplatform/common/security/SecurityConfiguration.java`

**Interfaces:**
- Produces a role-to-use-case matrix, include/extend relationships, permission boundaries, and page/API mapping for later review.

- [x] **Step 1: Define system boundary and actors**

  Record four human roles and OCR/MinIO as external actors, including responsibility, data scope, and system interactions.

- [x] **Step 2: Map business use cases**

  Group use cases into authentication, organization, archive/OCR, HR workflow, archive access/inventory, and statistics/audit. Include the full state transitions and exception paths already implemented in `docs/business-process-design.md`.

- [x] **Step 3: Add traceability and acceptance checks**

  Map core use cases to Vue routes and Spring endpoints, then add verifiable acceptance criteria for permissions, approvals, OCR review, archive return, inventory exceptions, and audit logging.

- [x] **Step 4: Review the document**

  Run `rg -n "TODO|TBD|待补充|待定" docs/use-case-analysis.md` and ensure no placeholders remain.

### Task 2: Create editable use-case diagram

**Files:**
- Create: `docs/diagrams/employee-hr-platform-use-case.drawio`
- Create: `docs/diagrams/employee-hr-platform-use-case.drawio.png` (rendered preview when renderer is available)

**Interfaces:**
- Produces a Draw.io diagram with system boundary `企业员工档案与人事流程管理平台`, grouped use cases, actors, and labeled `<<include>>` / `<<extend>>` relationships.

- [x] **Step 1: Create mxGraph XML**

  Use actor shapes outside the boundary, grouped use-case ellipses inside, and unique IDs for every vertex and edge. Escape Chinese labels and ampersands through XML entities where required.

- [x] **Step 2: Add relationship labels**

  Include authentication for business actions, OCR mapping/validation, manual review extension, offboarding handover confirmation, archive approval, and abnormal return extension.

- [x] **Step 3: Validate the XML**

  Run `xmllint --noout docs/diagrams/employee-hr-platform-use-case.drawio` or an equivalent XML parser and inspect dimensions/labels for overlap.

- [x] **Step 4: Render a preview**

  Exported the complete use-case diagram from Draw.io to `docs/diagrams/employee-hr-platform-use-case.drawio.png` and visually checked the full image. UML stereotype labels use escaped HTML entities so `<<include>>` and `<<extend>>` render as text.

### Task 3: Reconfigure the Vue application shell

**Files:**
- Modify: `web/src/layouts/AppLayout.vue`
- Modify: `web/src/router/index.ts`
- Modify: `web/src/styles.css` only when navigation spacing or responsive behavior needs adjustment

**Interfaces:**
- `AppLayout.vue` exposes role-filtered navigation sections and mobile overflow navigation using the existing `auth.canAccess` contract.
- `router/index.ts` provides `meta.title` and `meta.roles` for every protected route.

- [x] **Step 1: Define role aware navigation metadata**

  Group routes under `工作台`, `员工与流程`, `档案资源`, and `系统运营`; set role restrictions for inventory, organization, audit, and account administration.

- [x] **Step 2: Complete route metadata**

  Add titles for dashboard, employees, archive detail, OCR detail, workflows, contracts, archive access, inventory, and audit pages. Add `roles` for restricted pages.

- [x] **Step 3: Improve application entry context**

  Use a single active-route predicate for nested routes, keep the provided Logo, and show the current role and data scope in the shell without exposing implementation details.

- [x] **Step 4: Preserve mobile behavior**

  Keep the five primary mobile destinations and place role permitted secondary routes in the existing drawer.

### Task 4: Review route and role consistency

**Files:**
- Reference: `web/src/layouts/AppLayout.vue`
- Reference: `web/src/router/index.ts`
- Reference: `server/src/main/java/com/hrplatform/**/**Controller.java`

**Interfaces:**
- The same role boundaries must be reflected in navigation metadata and route guards; server annotations remain the authorization source of truth.

- [x] **Step 1: Compare every navigation entry to route metadata**

  Confirm that every route has a title and that each restricted link has a matching route role list.

- [x] **Step 2: Compare role lists to controller authorization**

  Confirm inventory uses `DEPT_MANAGER`, `HR_ADMIN`, `SYSTEM_ADMIN`; organization and audit use `HR_ADMIN`, `SYSTEM_ADMIN`; user administration uses `SYSTEM_ADMIN`.

- [x] **Step 3: Check nested route active states**

  Confirm archive and OCR detail URLs resolve to a relevant navigation group without marking a parent route incorrectly.

### Task 5: Build and review

**Files:**
- Reference: `web/package.json`
- Reference: `docs/use-case-analysis.md`
- Reference: `docs/diagrams/employee-hr-platform-use-case.drawio`

- [x] **Step 1: Build the frontend**

  Run `cd web && npm run build` and confirm `vue-tsc` and Vite both complete successfully.

- [x] **Step 2: Check diffs and diagram XML**

  Run `git diff --check` and `xmllint --noout docs/diagrams/employee-hr-platform-use-case.drawio`; verify all mxCell IDs are unique and the preview is a valid PNG.

- [x] **Step 3: Summarize deliverables**

  Frontend returned HTTP 200 on `127.0.0.1:5173`; backend health returned `UP` on `127.0.0.1:8080/actuator/health`. No process listens on OCR port `8000`; the PaddleOCR startup previously stalled during Python import and was interrupted, so the OCR engine is not running.
