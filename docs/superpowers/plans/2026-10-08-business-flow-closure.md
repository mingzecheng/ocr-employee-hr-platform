# 业务流程闭环与待办详情 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按业务流程图补齐统一待办详情、人事审批详情和 OCR 结果展示链路，并验证档案授权、盘点和合同流程的状态闭环。

**Architecture:** 后端继续复用现有领域服务和状态机，增加详情聚合接口与统一待办详情路由；前端用一个详情壳组件按待办类型加载对应业务数据，动作仍调用原领域接口，成功后重新加载详情和待办。OCR 预览和原图均通过 Spring 受控接口输出，前端不直接拼接 OCR 服务地址。

**Tech Stack:** Spring Boot 3, MyBatis, MySQL, Vue 3, TypeScript, Element Plus, PaddleOCR/mock OCR, MinIO。

## Global Constraints

- 保留原图、OCR 原始文本块和字段修订记录，不覆盖历史证据。
- 所有详情接口和动作接口执行角色权限、数据范围和状态机校验。
- 统一使用版本号乐观锁，状态冲突必须返回可见错误。
- 不新增与业务无关的个人信息字段。

### Task 1: 统一待办目标与人事申请详情

**Files:**
- Modify: `server/src/main/java/com/hrplatform/workflow/RequestController.java`
- Modify: `server/src/main/java/com/hrplatform/workflow/TodoService.java`
- Modify: `server/src/main/java/com/hrplatform/workflow/TodoItem.java`
- Modify: `server/src/main/java/com/hrplatform/workflow/RequestMapper.java`
- Modify: `server/src/main/resources/mapper/RequestMapper.xml`
- Create: `server/src/main/java/com/hrplatform/workflow/RequestDetail.java`
- Create: `server/src/main/java/com/hrplatform/workflow/TodoDetailController.java`
- Create: `server/src/main/java/com/hrplatform/workflow/TodoDetailService.java`

**Interfaces:**
- `GET /api/hr-requests/{id}` 返回人事申请详情和审批记录。
- `GET /api/todo-details/hr-request/{id}` 返回待办详情聚合数据。
- `TodoItem.targetPath` 改为 `/todos/hr-request/{id}`。

- [ ] 增加 `GET /api/hr-requests/{id}`，返回当前用户数据范围内的申请。
- [ ] 增加 `listApprovals(requestId)` 查询审批时间线，并在详情 DTO 中返回。
- [ ] 将 HR 待办目标改为统一详情路由，保留原列表查询参数兼容旧链接。
- [ ] 对已完成或无待办动作的申请返回只读详情，不改变历史状态。

### Task 2: 档案授权待办详情与动作闭环

**Files:**
- Modify: `server/src/main/java/com/hrplatform/access/ArchiveAccessController.java`
- Modify: `server/src/main/java/com/hrplatform/access/ArchiveAccessService.java`
- Modify: `server/src/main/java/com/hrplatform/workflow/TodoService.java`
- Modify: `web/src/views/ArchiveAccessView.vue`
- Create: `web/src/views/TodoDetailView.vue`
- Modify: `web/src/router/index.ts`

**Interfaces:**
- `/api/todo-details/archive-access/{id}` 返回申请、档案项和使用记录。
- `/api/todo-details/archive-return/{id}` 返回逾期/异常上下文。
- 动作沿用 `/api/archive-access/{id}/approve|use|return`。

- [ ] 统一待办详情页根据 `type` 加载授权详情，显示当前状态和允许动作。
- [ ] 审批、开始使用、归还、异常归还和移交后重新加载详情与待办。
- [ ] 处理已归还、已移交、已异常和已过期的只读状态。
- [ ] 让档案授权列表支持从统一详情返回并保持查询定位。

### Task 3: OCR 结果链路修复

**Files:**
- Modify: `server/src/main/java/com/hrplatform/ocr/OcrController.java`
- Modify: `server/src/main/java/com/hrplatform/ocr/OcrService.java`
- Modify: `server/src/main/java/com/hrplatform/archive/ArchiveController.java`
- Modify: `server/src/main/java/com/hrplatform/archive/ArchiveService.java`
- Modify: `web/src/views/OcrReviewView.vue`
- Modify: `web/src/api/http.ts`

**Interfaces:**
- `GET /api/archive/ocr-bindings/{bindingId}/preview` 输出检测框预览。
- `GET /api/archive/versions/{versionId}/download` 输出原图。
- OCR 详情同时返回 `bindingId`、`versionId`、`taskId`、`status`、`errorMessage` 和安全预览路径。

- [ ] 复现成功、失败、无预览和字段为空四种状态，记录实际返回 JSON。
- [ ] OCR 页使用受控预览地址并带登录态请求，打开原图调用档案下载接口。
- [ ] 失败状态展示错误码和重试按钮，重试后更新当前版本的绑定结果。
- [ ] 字段、文本块、修订列表始终按空数组渲染，修订后重新加载结果。
- [ ] 修复 `selectedType` 与档案版本实际 `documentType` 混用导致的映射问题。

### Task 4: 全流程运行核验与文档同步

**Files:**
- Modify: `scripts/verify-platform.sh`
- Modify: `docs/business-process-design.md`
- Modify: `README.md`

- [ ] 使用四个演示账号验证待办详情、审批、OCR 失败重试和授权归还。
- [ ] 验证盘点未解决异常不能完成，解决后可以归档。
- [ ] 验证合同登记、到期和续签状态不破坏员工流程。
- [ ] 执行 `mvn -q -f server/pom.xml -DskipTests package`、`cd web && npm run build` 和现有 smoke 脚本。
- [ ] 执行 `git diff --check` 并检查流程图 XML 可解析。
