# Workflow Access and Use Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 workflow-service 人事审批基础上实现档案访问申请、两级审批、领取使用、归还/移交和异常留痕闭环。

**Architecture:** 访问申请和使用记录由 workflow-service 独占 `workflow_db`，档案和员工 ID 作为跨服务逻辑引用，不建立跨库外键。命令接口在主库事务中写入申请、材料范围、审批历史、使用记录和审计日志；查询接口沿用只读事务路由。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring MVC, MyBatis, Flyway, MySQL 8, JUnit 5, MockMvc.

## Global Constraints

- 状态必须按 `DRAFT -> PENDING_DEPT_APPROVAL -> PENDING_HR_APPROVAL -> APPROVED -> IN_USE -> RETURNED/ABNORMAL` 流转。
- 驳回只能发生在部门审批或人事复核节点，并记录审批意见和审计日志。
- 领取只能针对 `APPROVED` 申请；归还/移交只能针对 `IN_USE` 使用记录。
- 缺件、损坏或超期时使用记录进入 `ABNORMAL`，保存异常说明，不得伪装成正常归还。
- 所有写入和写后返回使用主库事务；普通详情查询使用 `@Transactional(readOnly = true)`。
- 不复制档案文件或 OCR 原始数据；只保存业务 ID、用途、期限和状态证据。
- 每个行为先写失败测试，再写最小实现。

---

### Task 1: Access application persistence and state service

**Files:**
- Create: `backend/workflow-service/src/main/resources/db/migration/V2__archive_access_schema.sql`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/access/ArchiveAccessApplication.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/access/ArchiveAccessItem.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/access/ArchiveUseRecord.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/access/ArchiveAccessMapper.java`
- Create: `backend/workflow-service/src/main/resources/mapper/ArchiveAccessMapper.xml`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/access/ArchiveAccessDtos.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/access/ArchiveAccessService.java`
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/access/ArchiveAccessServiceTest.java`
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/access/ArchiveAccessPersistenceTest.java`

**Interfaces:**
- `create(CreateRequest, long)` creates a `DRAFT` application and its immutable material scope.
- `submit(long, long)` moves only the applicant's draft to `PENDING_DEPT_APPROVAL`.
- `approve(long, long, boolean, String)` advances department approval to HR approval and HR approval to `APPROVED`; rejection moves to `REJECTED`.
- `checkout(long, long)` creates one `IN_USE` record for an approved application.
- `returnUse(long, long, ReturnRequest)` moves the use record to `RETURNED` or `ABNORMAL` based on missing/damaged/overdue data.
- `get(long)` returns the application, items, current use record and approval history.

- [x] Write focused service tests for create, illegal transitions, two-level approval, checkout, normal return and abnormal return.
- [x] Run focused tests and confirm they fail because access classes and behavior do not exist.
- [x] Add V2 schema, mapper contracts, service state transitions and append-only audit writes.
- [x] Run focused tests and persistence tests against the local workflow MySQL database.

### Task 2: HTTP API and gateway contract

**Files:**
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/access/ArchiveAccessController.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/access/ArchiveAccessExceptionHandler.java`
- Test: `backend/workflow-service/src/test/java/com/hrplatform/workflow/access/ArchiveAccessControllerTest.java`
- Modify: `backend/gateway-service/src/test/java/com/hrplatform/gateway/GatewayRouteConfigurationTest.java`

**Interfaces:**
- `POST /api/archive-access-applications`
- `POST /api/archive-access-applications/{id}/submit`
- `POST /api/archive-access-applications/{id}/approve`
- `POST /api/archive-access-applications/{id}/reject`
- `POST /api/archive-access-applications/{id}/checkout`
- `GET /api/archive-access-applications/{id}`
- `POST /api/archive-uses/{id}/return`

- [x] Write MockMvc tests for authentication principal propagation, response envelopes, validation and state errors.
- [x] Run controller tests and confirm the endpoints are absent/failing.
- [x] Implement controller DTO binding, stable error codes and route assertions.
- [x] Run workflow and gateway tests.

### Task 3: End-to-end verification and documentation

**Files:**
- Modify: `scripts/verify-platform.sh`
- Modify: `docs/需求理解.md`
- Modify: `docs/开发验收记录.md`

- [x] Extend the verification flow with create, submit, two approvals, checkout and abnormal return.
- [x] Run workflow unit, MockMvc, persistence, gateway and platform verification tests.
- [x] Record the real state transitions and remaining inventory/statistics work.

## Verification Gate

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml -pl workflow-service test
bash scripts/verify-platform.sh
```
