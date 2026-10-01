# Platform Development Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在已验证的 OCR 档案闭环基础上，完成微服务平台的有效设计基线、Redis 业务缓存、Vue 管理端和后续人事流程开发路径。

**Architecture:** 保留 gateway、identity、archive、workflow 四个核心 Java 服务和独立 OCR 服务。每个业务服务使用自己的 MySQL 逻辑库，通过公共 `AbstractRoutingDataSource` 按事务只读属性路由主库/副本；Redis 只缓存权限、部门树、列表摘要、幂等键和短时锁；Vue 管理端只访问网关。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring Security, MyBatis, MySQL 8, Redis 7, Spring Cloud Gateway, Vue 3, Vite, TypeScript, Element Plus, Pinia, Axios, ECharts, Python FastAPI, PaddleOCR, MinIO.

## Global Constraints

- 服务只通过 HTTP 和业务 ID 交互，不跨库查询，不建立跨库外键。
- 所有写入和写后确认读取使用主库；`@Transactional(readOnly = true)` 查询使用副本。
- Redis 不保存文件二进制、原始 OCR JSON 或不可重建业务事实。
- OCR 原始结果、原图和检测框效果图由 OCR 服务保存，档案库保存关联和元数据。
- 前端只配置网关地址，不直接访问身份、档案、流程或 OCR 内部端口。
- 每个行为先写失败测试，再写最小实现；每项完成后运行对应模块测试和端到端验证。
- 当前文档中的 2026-09-17 微服务方案和 `docs/数据库设计.md` 覆盖更早的模块化单体草案。

---

### Task 1: 固化需求、方案和数据库设计基线

**Files:**
- Create: `docs/数据库设计.md`
- Create: `docs/superpowers/plans/2026-09-17-platform-development.md`
- Modify: `docs/需求理解.md`（追加当前阶段有效基线和验收清单）
- Modify: `docs/方案设计.md`（追加历史版本覆盖说明）

**Interfaces:**
- 需求文档定义 P0/P1/P2 范围、角色、状态和验收口径。
- 数据库设计定义 `identity_db`、`archive_db`、`workflow_db` 的表职责、索引、逻辑引用、读写路由和 Redis key。

- [x] **Step 1: 核对现有需求、方案、迁移和运行服务。**
- [x] **Step 2: 写入数据库设计和本实施计划。**
- [x] **Step 3: 追加文档有效版本说明和第一阶段验收清单。**
- [x] **Step 4: 自检文档冲突、占位词和实际迁移差异。**
- [x] **Step 5: 审核文档后再进入代码任务。**

### Task 2: 部门树 Redis cache-aside

**Files:**
- Modify: `backend/platform-common/platform-common-core/src/main/java/com/hrplatform/common/cache/CacheKeys.java`
- Modify: `backend/identity-service/src/main/java/com/hrplatform/identity/org/DepartmentService.java`
- Test: `backend/identity-service/src/test/java/com/hrplatform/identity/org/DepartmentServiceTest.java`

**Interfaces:**
- `CacheKeys.departmentTree()` 返回 `hr:org:department:tree`。
- `DepartmentService.findActiveTree()` 先从 JSON Redis 读取，未命中时走只读副本并写入 10 分钟 TTL。
- Redis 故障时查询仍返回数据库结果，不改变业务正确性。

- [x] **Step 1: 写测试覆盖命中、未命中回源、TTL 和 Redis 异常降级。**
- [x] **Step 2: 运行测试确认缓存行为尚未实现。**
- [x] **Step 3: 增加 cache-aside 实现和 key helper。**
- [x] **Step 4: 运行 identity-service 测试和读写路由测试。**

### Task 3: 档案列表 Redis 缓存与失效

**Files:**
- Modify: `backend/platform-common/platform-common-core/src/main/java/com/hrplatform/common/cache/CacheKeys.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/employee/EmployeeService.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveService.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/employee/EmployeeServiceTest.java`

**Interfaces:**
- `CacheKeys.archiveList(String normalizedQuery)` 返回稳定的 `hr:list:archive:{sha256}` key。
- 员工列表只缓存脱敏 DTO，TTL 60 秒；创建员工、上传档案和版本写入成功后删除相关前缀。
- Redis 未命中或不可用时直接走只读副本。

- [x] **Step 1: 写列表命中、回源、空结果和写后失效测试。**
- [x] **Step 2: 运行测试确认失败。**
- [x] **Step 3: 实现缓存与事务提交后的失效。**
- [x] **Step 4: 运行 archive-service 全量测试并验证 Redis key。**

### Task 4: Vue 管理端工程和网关 API 客户端

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/stores/auth.ts`
- Create: `frontend/src/api/http.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/api/archive.ts`
- Create: `frontend/src/types/api.ts`
- Test: `frontend/src/stores/auth.test.ts`

**Interfaces:**
- Axios base URL 使用 `VITE_API_BASE_URL`，默认 `/api`，请求自动附加 JWT。
- `authStore.login(username, password)` 调用 `/auth/login`，保存 token 和用户信息；401 清除状态并回到登录页。
- `archiveApi.listEmployees`, `uploadDocument`, `runOcr`, `getOcrResult`, `getDetectionPreviewUrl` 只访问网关路径。

- [x] **Step 1: 写 auth store 的 token 持久化、登录成功和 401 清理测试。**
- [x] **Step 2: 使用 Vite/Vitest 运行测试确认工程初始化状态。**
- [x] **Step 3: 创建 Vue 3 + TypeScript + Element Plus + Pinia + Router 工程。**
- [x] **Step 4: 实现 API 类型、Axios 拦截器和认证 store。**
- [x] **Step 5: 运行前端单测、TypeScript 检查和生产构建。**

### Task 5: Vue 档案 OCR 工作台

**Files:**
- Create: `frontend/src/layouts/AppLayout.vue`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/DashboardView.vue`
- Create: `frontend/src/views/EmployeeListView.vue`
- Create: `frontend/src/views/EmployeeArchiveView.vue`
- Create: `frontend/src/components/OcrResultPanel.vue`
- Create: `frontend/src/components/DetectionPreview.vue`
- Create: `frontend/src/styles/index.css`
- Test: `frontend/src/components/OcrResultPanel.test.ts`

**Interfaces:**
- 登录后显示员工列表；员工详情页支持 PNG/JPEG 上传、触发 OCR、展示文本块/字段/置信度和检测框效果图。
- 检测框通过档案代理接口以 blob URL 加载，不暴露 OCR 内部地址。
- OCR 失败、预览缺失、401、上传格式错误和空结果都有明确页面状态。

- [x] **Step 1: 写 OCR 结果面板的成功、失败和空预览测试。**
- [x] **Step 2: 实现布局、路由保护和页面状态。**
- [x] **Step 3: 实现上传、识别、轮询/刷新、字段修订和预览下载。**
- [x] **Step 4: 运行前端单测、构建并用 Playwright 验证登录到预览流程。**

### Task 6: workflow-service 数据库和最小流程闭环

**Files:**
- Create: `backend/workflow-service/src/main/resources/db/migration/V1__workflow_schema.sql`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/config/WorkflowDataSourceConfiguration.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/request/RequestController.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/request/RequestService.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/request/RequestMapper.java`
- Create: `backend/workflow-service/src/main/resources/mapper/RequestMapper.xml`
- Tests: workflow mapper/service/controller tests

**Interfaces:**
- `POST /api/hr-requests` 创建入职、调动或离职草稿。
- `POST /api/hr-requests/{id}/submit` 提交申请。
- `POST /api/approvals/{id}/approve` 和 `/reject` 写审批历史并迁移状态。
- 每次提交和审批在主库事务中写入申请、审批记录和审计日志；查询走副本。

- [x] **Step 1: 写状态迁移和唯一申请号失败测试。**
- [x] **Step 2: 运行测试确认迁移和接口不存在时失败。**
- [x] **Step 3: 添加 workflow schema、MyBatis mapper 和服务实现。**
- [x] **Step 4: 接入网关路由和服务认证；Redis 待办缓存及细粒度审批权限列入后续 P1。**
- [x] **Step 5: 运行 workflow、gateway 和端到端流程测试。**

### Task 7: 平台验收与文档同步

**Files:**
- Modify: `scripts/verify-platform.sh`
- Modify: `backend/README.md`
- Create: `frontend/README.md`
- Create: `docs/开发验收记录.md`

- [x] **Step 1: 增加 Redis、前端构建、登录、员工、档案 OCR、预览和最小审批闭环检查。**
- [x] **Step 2: 分别使用 identity/archive/workflow 数据库环境执行后端测试。**
- [x] **Step 3: 运行 OCR Python 测试和前端构建。**
- [x] **Step 4: 启动本地完整服务并完成网关端到端验收。**
- [x] **Step 5: 记录真实结果、已知限制和后续 P1 范围。**

## Verification Gate

至少需要以下证据才能结束第一阶段：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/identity_db?...' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/identity_db?...' \
MYSQL_USERNAME=hr MYSQL_PASSWORD='...' \
mvn -f backend/pom.xml -pl identity-service test
```

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?...' \
MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?...' \
MYSQL_USERNAME=hr MYSQL_PASSWORD='...' \
mvn -f backend/pom.xml -pl archive-service test
```

```bash
cd ocr-service && .venv/bin/python -m pytest -q
cd frontend && npm test -- --run && npm run build
```

第一阶段端到端验收必须证明：登录成功、员工列表可见、图片上传成功、OCR 返回任务和文本块、检测框 PNG 可经网关加载，且重复 OCR 请求保持幂等。

## 执行备注

本阶段运行服务必须使用与当前源码匹配的公共模块。若通过 `spring-boot:run` 单独启动服务，先执行 `mvn -pl <service> -am install`；否则可能从本地 Maven 仓库加载旧版 `platform-common` JAR，导致运行时 `NoSuchMethodError`。MinIO 凭据也必须与 Compose 实例一致。
