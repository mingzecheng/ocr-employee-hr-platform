# Inventory, Statistics, and Permissions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成盘点异常、档案统计、细粒度权限和真实看板数据接入，补齐首期 P0 验收闭环。

**Architecture:** workflow-service 独占盘点任务、盘点明细和异常处理状态；archive-service 基于本服务数据库提供员工、材料、OCR 和完整率聚合统计；identity-service 以权限编码扩展种子数据，业务 Controller 用方法级权限保护；gateway 仅增加 statistics 路由，前端 Dashboard 调用统计接口展示真实数据。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring Security method security, MyBatis, Flyway, MySQL 8, JUnit 5, MockMvc, Vue 3, TypeScript, Vitest.

## Global Constraints

- 盘点状态必须可追溯：`DRAFT -> IN_PROGRESS -> COMPLETED`，存在差异时任务进入 `ABNORMAL`，异常处理后保留处理记录。
- 盘点明细记录应盘档案版本与实际状态，差异说明不得覆盖原始盘点结果。
- 统计只读取 archive-service 自有数据库，不跨库访问 workflow 或 identity 表。
- 统计接口统一返回 `code`、`message`、`data`、`traceId`，普通查询使用只读事务。
- 业务权限使用 JWT 中的 permission authority，未授权请求必须返回 `403/FORBIDDEN`。
- 每个行为先写失败测试，再写最小实现；完成后执行 Java、前端和真实网关验收。

---

### Task 1: Inventory task state and exception handling

**Files:**
- Create: `backend/workflow-service/src/main/resources/db/migration/V3__inventory_schema.sql`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/inventory/InventoryTask.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/inventory/InventoryItem.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/inventory/InventoryException.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/inventory/InventoryMapper.java`
- Create: `backend/workflow-service/src/main/resources/mapper/InventoryMapper.xml`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/inventory/InventoryDtos.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/inventory/InventoryService.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/inventory/InventoryController.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/inventory/InventoryExceptionHandler.java`
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/inventory/InventoryServiceTest.java`
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/inventory/InventoryControllerTest.java`
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/inventory/InventoryPersistenceTest.java`

**Interfaces:**
- `POST /api/inventory-tasks` creates a `DRAFT` task with immutable expected archive version scope.
- `POST /api/inventory-tasks/{id}/start` moves `DRAFT` to `IN_PROGRESS`.
- `POST /api/inventory-tasks/{id}/items/{itemId}/check` records actual version, condition and difference without overwriting expected values.
- `POST /api/inventory-tasks/{id}/complete` moves to `COMPLETED` when all items match, otherwise `ABNORMAL`.
- `POST /api/inventory-tasks/{id}/resolve` records abnormal handling and moves `ABNORMAL` to `COMPLETED`.
- `GET /api/inventory-tasks/{id}` returns task, immutable scope, check results and resolution records.

- [x] Write service tests for lifecycle, missing/damaged/different items, illegal transitions and resolution.
- [x] Run focused tests and confirm the inventory classes/schema are absent.
- [x] Add V3 schema, mapper, service state transitions, controller and audit records.
- [x] Run workflow unit, MockMvc and persistence tests.

### Task 2: Archive statistics query API

**Files:**
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/statistics/StatisticsMapper.java`
- Create: `backend/archive-service/src/main/resources/mapper/StatisticsMapper.xml`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/statistics/StatisticsService.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/statistics/StatisticsDtos.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveController.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/statistics/StatisticsServiceTest.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/statistics/StatisticsControllerTest.java`
- Test: `backend/archive-service/src/test/java/com/hrplatform/archive/statistics/StatisticsPersistenceTest.java`

**Interfaces:**
- `GET /api/statistics/overview` returns active employee count, archive document count, OCR task totals, OCR success rate, archive completeness rate and pending review count.
- Completeness is the ratio of active employees with at least one active archive document and one published version to active employees; zero active employees returns `0`.
- OCR success rate is successful OCR bindings divided by all OCR bindings; zero bindings returns `0`.

- [x] Write unit/controller tests for non-zero and zero-denominator aggregation.
- [x] Run tests red.
- [x] Implement read-only SQL aggregation and response envelope.
- [x] Run archive tests and verify the persistence query against local MySQL.

### Task 3: Permission seed and endpoint guards

**Files:**
- Create: `backend/identity-service/src/main/resources/db/migration/V3__identity_permissions.sql`
- Modify: `backend/identity-service/src/test/java/com/hrplatform/identity/config/IdentitySeedInitializerTest.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveController.java`
- Modify: `backend/workflow-service/src/main/java/com/hrplatform/workflow/request/RequestController.java`
- Modify: `backend/workflow-service/src/main/java/com/hrplatform/workflow/access/ArchiveAccessController.java`
- Modify: `backend/workflow-service/src/main/java/com/hrplatform/workflow/inventory/InventoryController.java`
- Test: relevant controller security tests in archive, workflow and identity modules

**Permission codes:** `ARCHIVE_READ`, `ARCHIVE_WRITE`, `ARCHIVE_ACCESS_CREATE`, `ARCHIVE_ACCESS_APPROVE`, `ARCHIVE_ACCESS_CHECKOUT`, `ARCHIVE_ACCESS_RETURN`, `INVENTORY_READ`, `INVENTORY_WRITE`, `STATISTICS_READ`.

- [x] Add seed assertions for all permission codes and role mappings.
- [x] Run identity tests red.
- [x] Add migration and `@PreAuthorize` guards to read/write/approval endpoints.
- [x] Run controller permission contract tests and real gateway forbidden-request verification.

### Task 4: Gateway, Dashboard, and end-to-end verification

**Files:**
- Modify: `backend/gateway-service/src/main/resources/application.yml`
- Modify: `backend/gateway-service/src/test/java/com/hrplatform/gateway/GatewayRouteConfigurationTest.java`
- Modify: `frontend/src/api/statistics.ts`
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/views/DashboardView.vue`
- Modify: `frontend/src/views/DashboardView.test.ts`
- Modify: `scripts/verify-platform.sh`
- Modify: `docs/开发验收记录.md`

- [x] Add `/api/statistics/**` gateway route and route test.
- [x] Write Dashboard loading, value binding and error-state tests.
- [x] Connect real overview data and preserve a usable loading/error state.
- [x] Extend platform verification for inventory difference/resolution, statistics response and permission denial.
- [x] Run all module tests, frontend build and the configured real gateway verification command.

## Verification Gate

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml test
npm --prefix frontend test -- --run
npm --prefix frontend run build
bash scripts/verify-platform.sh
```
