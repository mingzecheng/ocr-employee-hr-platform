# 前端工作台完善 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完善员工档案管理前端的业务闭环、服务端分页、新建员工、概览快捷操作和 OCR 低置信度复核，并保持蓝色商务风格。

**Architecture:** 继续使用 Vue 3 + TypeScript + Element Plus。后端员工接口改为返回分页对象，前端 API 层统一解包分页数据；页面层只负责状态和交互，沿用现有 `http`、路由和组件。OCR 复核状态在 `OcrResultPanel` 内计算，不改变 OCR 结果的后端字段结构。

**Tech Stack:** Vue 3, TypeScript, Vite, Vitest, Vue Test Utils, Element Plus, Spring Boot, MyBatis, MySQL.

## Global Constraints

- 保持商务风格，以深蓝和浅蓝灰为主色。
- 低置信度阈值固定为 80%，空置信度视为待复核。
- 新增请求沿用现有 JWT、`http` 和 `unwrap` 封装。
- 员工分页继续遵守 `PERM_ARCHIVE_READ` 与 `PERM_ARCHIVE_WRITE` 数据权限。
- 不改变数据库表结构，不引入新的运行时依赖。

---

### Task 1: 员工分页 API 契约

**Files:**
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveDtos.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveController.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/employee/EmployeeMapper.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/employee/EmployeeMapper.xml`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/employee/EmployeeService.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/api/ArchiveControllerTest.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/employee/EmployeeServiceTest.java`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/api/archive.ts`

**Interfaces:**
- Produce `ArchiveDtos.EmployeePageData(List<EmployeeData> items, long total, int page, int pageSize)`.
- Produce `EmployeeMapper.countInScope(String scopeType, Long employeeId, Long departmentId)`.
- Produce frontend `PageData<T>` and `listEmployees(page, pageSize): Promise<PageData<Employee>>`.

- [x] **Step 1: Write failing backend tests** asserting controller JSON contains `data.items`, `data.total`, `data.page`, and `data.pageSize`, and service calls the scoped count query.
- [x] **Step 2: Run targeted backend tests** with `mvn -pl archive-service -am -Dtest=ArchiveControllerTest,EmployeeServiceTest test`; expected failure because the page DTO and count method do not exist.
- [x] **Step 3: Implement the page DTO, scoped count mapper SQL, service result, and controller generic type.** Cache only the page items plus metadata under the existing scope/page cache key.
- [x] **Step 4: Update frontend `PageData<T>`, `listEmployees`, and all callers to use `.items` and `.total`.
- [x] **Step 5: Run backend targeted tests and frontend typecheck.** Targeted tests pass with `-DargLine=-Dnet.bytebuddy.experimental=true` on Java 26; `npm run build` typechecks.

### Task 2: 员工列表与新建员工

**Files:**
- Modify: `frontend/src/views/EmployeeListView.vue`
- Modify: `frontend/src/views/EmployeeListView.test.ts`
- Modify: `frontend/src/styles/index.css`

**Interfaces:**
- Consume `listEmployees(page, pageSize): Promise<PageData<Employee>>` and `createEmployee(input): Promise<Employee>`.
- Produce a form with employee number, name, optional ID card, optional department ID; preserve form data on failure.

- [x] **Step 1: Add failing component tests** for server-page requests, page navigation, new employee submission, and inline error retention.
- [x] **Step 2: Run `npm test -- --run src/views/EmployeeListView.test.ts`; expected failure because the page uses the old array contract and has no create form.
- [x] **Step 3: Implement server pagination, filter reset, refresh retry, and a modal-like inline form panel using existing Element Plus buttons and native inputs.
- [x] **Step 4: Add blue business styling for the create panel, toolbar status, pagination, and mobile layout.
- [x] **Step 5: Run the focused test and inspect the rendered empty/loading/error states.

### Task 3: 概览快捷入口

**Files:**
- Modify: `frontend/src/views/DashboardView.vue`
- Modify: `frontend/src/views/DashboardView.test.ts`
- Modify: `frontend/src/styles/index.css`

- [x] **Step 1: Add failing tests** asserting four shortcut labels and their router destinations.
- [x] **Step 2: Run the focused dashboard test and confirm the new shortcut assertions fail.
- [x] **Step 3: Add a compact shortcut strip for employee creation, HR workflow, archive authorization, and inventory; keep all buttons keyboard reachable.
- [x] **Step 4: Add responsive styles and preserve existing independent loading/error states.
- [x] **Step 5: Run dashboard tests and the existing TodoSummary tests.

### Task 4: OCR 低置信度复核

**Files:**
- Modify: `frontend/src/components/OcrResultPanel.vue`
- Modify: `frontend/src/components/OcrResultPanel.test.ts`
- Modify: `frontend/src/styles/index.css`

- [x] **Step 1: Add failing tests** for low-confidence count, filter switching, missing-value filter, and failed correction retaining edit mode.
- [x] **Step 2: Run the focused OCR tests and confirm the new assertions fail.
- [x] **Step 3: Implement derived review status, field filters, an explicit review summary, and correction error state without clearing the result.
- [x] **Step 4: Add responsive styling for the filter bar, review badges, and detection preview action row.
- [x] **Step 5: Run all frontend tests and build.

### Task 5: End-to-end verification

**Files:**
- No new source files.
- Inspect: `frontend/src/router/index.ts`, `frontend/src/styles/index.css`, and all changed views.

- [x] **Step 1: Run `npm test -- --run` and confirm zero failed tests.
- [x] **Step 2: Run `npm run build` and confirm `vue-tsc` and Vite exit successfully.
- [x] **Step 3: Start or reuse the local frontend server and verify desktop and 390px mobile screenshots for dashboard, employee list, employee archive OCR, and workflow navigation.
- [x] **Step 4: Check browser console and network errors for the modified flows; current running services report OCR/archive `502`/`403` on the existing fixture, and the UI presents those states explicitly.
- [x] **Step 5: Report remaining non-blocking bundle-size warnings separately from functional results.
