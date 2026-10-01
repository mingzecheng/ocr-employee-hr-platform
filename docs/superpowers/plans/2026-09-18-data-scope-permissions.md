# Data Scope Permissions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前仅有接口级权限的实现补齐为可验证的数据范围权限，确保员工、档案、OCR 资源和统计结果只返回当前用户有权访问的数据。

**Architecture:** identity 在用户记录和 JWT 中保存 `departmentId`，公共安全模块从角色、用户部门和员工归属计算不可变 `DataScope`。archive 将 scope 传入 MyBatis 查询和资源归属校验，所有范围型 Redis key 都包含 scope 指纹。workflow 不跨库查询 archive 表；通过 archive-service 内部授权 API 校验员工、材料和版本，并在 workflow_db 保存目标部门及盘点员工/部门快照。

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Security, JJWT, MyBatis, Flyway, MySQL, Redis, JUnit 5, Mockito.

## Global Constraints

- 普通员工只能访问本人数据；`DEPT_MANAGER` 只能访问 JWT 中 `departmentId` 对应部门；`SYSTEM_ADMIN` 和 `HR_ADMIN` 可访问全组织。
- 缺少员工或部门上下文时不得扩大权限，默认返回空范围或 `403/FORBIDDEN`。
- 数据范围必须在 SQL 和单资源校验中生效，不能只依赖 Controller 的 `@PreAuthorize`。
- Redis 员工列表缓存键必须包含 scope 指纹，不能跨用户复用结果。
- 不跨服务直接读取 workflow 或 identity 数据库表。
- 每个行为先写失败测试，再写最小生产代码；使用显式 MySQL 主从环境串行运行 Maven 测试。

---

### Task 1: JWT Data Scope Context

**Files:**
- Create: `backend/platform-common/platform-common-security/src/main/java/com/hrplatform/common/security/DataScope.java`
- Create: `backend/platform-common/platform-common-security/src/test/java/com/hrplatform/common/security/DataScopeTest.java`
- Modify: `backend/platform-common/platform-common-security/src/main/java/com/hrplatform/common/security/JwtPrincipal.java`
- Modify: `backend/platform-common/platform-common-security/src/main/java/com/hrplatform/common/security/JwtService.java`
- Create: `backend/platform-common/platform-common-security/src/test/java/com/hrplatform/common/security/JwtServiceTest.java`
- Modify: `backend/identity-service/src/main/java/com/hrplatform/identity/user/SysUser.java`
- Modify: `backend/identity-service/src/main/java/com/hrplatform/identity/user/SysUserMapper.java`
- Modify: `backend/identity-service/src/main/resources/mapper/SysUserMapper.xml`
- Modify: `backend/identity-service/src/main/java/com/hrplatform/identity/auth/AuthService.java`
- Modify: `backend/identity-service/src/main/java/com/hrplatform/identity/auth/CurrentUserData.java`
- Create: `backend/identity-service/src/main/resources/db/migration/V4__user_data_scope.sql`

**Interfaces:**
- `DataScope.fromPrincipal(JwtPrincipal)` returns `ALL`, `DEPARTMENT`, `EMPLOYEE`, or `NONE`.
- `DataScope` exposes `type()`, `employeeId()`, `departmentId()`, `allowsEmployee(long, Long)`, and `cacheKey()`.
- `JwtService.issue(..., Long employeeId, Long departmentId)` and `parse()` round-trip the `departmentId` claim.

- [x] **Step 1: Write the failing tests**

```java
@Test
void managersUseDepartmentScopeAndEmployeesUseOwnEmployeeScope() {
    assertThat(DataScope.fromPrincipal(new JwtPrincipal(1L, "manager",
            List.of("DEPT_MANAGER"), List.of(), 9L, 12L)).type())
            .isEqualTo(DataScope.Type.DEPARTMENT);
    assertThat(DataScope.fromPrincipal(new JwtPrincipal(2L, "employee",
            List.of("EMPLOYEE"), List.of(), 9L, null)).allowsEmployee(9L, 88L)).isTrue();
    assertThat(DataScope.fromPrincipal(new JwtPrincipal(2L, "employee",
            List.of("EMPLOYEE"), List.of(), 9L, null)).allowsEmployee(10L, 88L)).isFalse();
}

@Test
void jwtPreservesDepartmentContext() {
    JwtService service = new JwtService("12345678901234567890123456789012", "test", Duration.ofMinutes(5));
    String token = service.issue(7L, "manager", List.of("DEPT_MANAGER"), List.of(), 42L, 12L).value();
    assertThat(service.parse(token).departmentId()).isEqualTo(12L);
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `mvn -pl platform-common/platform-common-security -am -Dtest=DataScopeTest,JwtServiceTest test`

Expected: FAIL because `DataScope` and the six-argument JWT/context API do not exist.

- [x] **Step 3: Write the minimal implementation**

Add `departmentId` to `JwtPrincipal` and to the `employee_id`/`department_id` user query mapping. Implement role precedence `SYSTEM_ADMIN/HR_ADMIN -> ALL`, `DEPT_MANAGER + departmentId -> DEPARTMENT`, `employeeId -> EMPLOYEE`, otherwise `NONE`; expose a stable scope fingerprint for cache keys. Add Flyway `V4` with a nullable `sys_user.department_id` column and index, and issue/parse the claim.

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn -pl platform-common/platform-common-security -am -Dtest=DataScopeTest,JwtServiceTest test`

Expected: PASS.

### Task 2: Employee List and Archive Resource Filtering

**Files:**
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/api/DataScopeDeniedException.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/employee/EmployeeMapper.java`
- Modify: `backend/archive-service/src/main/resources/mapper/EmployeeMapper.xml`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/employee/EmployeeService.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveMapper.java`
- Modify: `backend/archive-service/src/main/resources/mapper/ArchiveMapper.xml`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/ocr/OcrBindingMapper.java`
- Modify: `backend/archive-service/src/main/resources/mapper/OcrBindingMapper.xml`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/document/ArchiveService.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveController.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/api/ArchiveExceptionHandler.java`
- Modify: `backend/platform-common/platform-common-core/src/main/java/com/hrplatform/common/cache/CacheKeys.java`
- Modify: `backend/archive-service/src/test/java/com/hrplatform/archive/employee/EmployeeServiceTest.java`
- Modify: `backend/archive-service/src/test/java/com/hrplatform/archive/document/ArchiveServiceTest.java`

**Interfaces:**
- Mapper methods accept `scopeType`, `employeeId`, and `departmentId` parameters and apply a SQL `choose` branch; `NONE` always matches no row.
- `EmployeeService.list(int page, int pageSize, DataScope scope)` and `create(..., DataScope scope)` enforce scope before database/cache access.
- Archive version and OCR binding queries return only rows joined through an employee allowed by the scope.
- Existing missing-resource behavior remains `ARCHIVE_NOT_FOUND`; scope denial is `403/FORBIDDEN` without revealing resource existence.

- [x] **Step 1: Write the failing tests**

```java
@Test
void listUsesScopeSpecificCacheKeyAndMapperArguments() {
    DataScope scope = DataScope.employee(7L, 9L);
    EmployeeMapper mapper = mock(EmployeeMapper.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(any())).thenReturn(null);
    when(mapper.list(0, 20, "EMPLOYEE", 9L, null)).thenReturn(List.of());

    new EmployeeService(mapper, redis, new ObjectMapper()).list(1, 20, scope);

    verify(values).get(argThat(key -> key.contains(scope.cacheKey())));
    verify(mapper).list(0, 20, "EMPLOYEE", 9L, null);
}

@Test
void archiveResourceFromAnotherDepartmentIsDenied() {
    when(employeeMapper.findById(7L, "DEPARTMENT", null, 12L)).thenReturn(null);
    assertThatThrownBy(() -> service.uploadDocument(7L, "profile", null,
            image(), 9L, DataScope.department(12L, 2L)))
            .isInstanceOf(DataScopeDeniedException.class);
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `mvn -pl archive-service -am -Dtest=EmployeeServiceTest,ArchiveServiceTest test`

Expected: FAIL because scope-aware signatures and authorization checks do not exist.

- [x] **Step 3: Write the minimal implementation**

Pass `DataScope` from `Authentication.getPrincipal()` in every archive Controller endpoint. Add scoped SQL predicates for employee, version, and OCR binding ownership. Include `scope.cacheKey()` in `CacheKeys.archiveList(...)`. Reject `NONE` and out-of-scope resources with `AccessDeniedException`/`DataScopeDeniedException`; perform the check before reading object storage or calling OCR. Preserve existing OCR preview missing mapping after authorization succeeds.

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn -pl archive-service -am -Dtest=EmployeeServiceTest,ArchiveServiceTest,ArchiveControllerTest test`

Expected: PASS.

### Task 3: Scope-Aware Statistics

**Files:**
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/statistics/StatisticsMapper.java`
- Modify: `backend/archive-service/src/main/resources/mapper/StatisticsMapper.xml`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/statistics/StatisticsService.java`
- Modify: `backend/archive-service/src/main/java/com/hrplatform/archive/statistics/StatisticsController.java`
- Modify: `backend/archive-service/src/test/java/com/hrplatform/archive/statistics/StatisticsServiceTest.java`
- Modify: `backend/archive-service/src/test/java/com/hrplatform/archive/statistics/StatisticsPersistenceTest.java`

**Interfaces:**
- `StatisticsMapper.aggregate(String scopeType, Long employeeId, Long departmentId)` uses one employee-scope predicate in every subquery.
- `StatisticsService.overview(DataScope scope)` returns zero-valued aggregates for `NONE` and never calls an unscoped aggregate.

- [x] **Step 1: Write the failing tests**

```java
@Test
void passesTheCurrentScopeToEveryStatisticsAggregate() {
    DataScope scope = DataScope.department(12L, 7L);
    when(mapper.aggregate("DEPARTMENT", null, 12L))
            .thenReturn(new StatisticsMapper.StatisticsAggregate(2L, 3L, 2L, 1L, 1L, 1L, 1L));

    assertThat(service.overview(scope).activeEmployeeCount()).isEqualTo(2L);
    verify(mapper).aggregate("DEPARTMENT", null, 12L);
}

@Test
void emptyScopeCannotReadGlobalStatistics() {
    assertThat(service.overview(DataScope.none(99L)).activeEmployeeCount()).isZero();
    verifyNoInteractions(mapper);
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `mvn -pl archive-service -am -Dtest=StatisticsServiceTest test`

Expected: FAIL because statistics has no scope parameter and currently invokes a global query.

- [x] **Step 3: Write the minimal implementation**

Add the scope parameters to the mapper and apply the same `employee`/`department`/`ALL` predicate to active employees, documents, OCR bindings, pending review, and completeness subqueries. Make the controller pass `DataScope.fromPrincipal(...)`.

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn -pl archive-service -am -Dtest=StatisticsServiceTest,StatisticsControllerTest,StatisticsPersistenceTest test`

Expected: PASS.

### Task 4: End-to-End Verification and Documentation

**Files:**
- Modify: `scripts/verify-platform.sh`
- Modify: `docs/需求理解.md`
- Modify: `docs/数据库设计.md`
- Modify: `docs/开发验收记录.md`

- [x] **Step 1: Add an executable scope acceptance flow**

The verification script must assert that an employee token cannot list another employee, a department manager cannot read another department's employee or OCR preview, and an administrator still sees the full aggregate. Assert `403`/`FORBIDDEN` and assert that the protected resource is not fetched from OCR/object storage through service tests.

- [x] **Step 2: Run focused and integration verification**

Run serially with the running local infrastructure:

```bash
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/identity_db?...' MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/identity_db?...' mvn -pl identity-service -am test
MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:13306/archive_db?...' MYSQL_READ_URL='jdbc:mysql://127.0.0.1:13307/archive_db?...' mvn -pl archive-service -am test
bash scripts/verify-platform.sh
```

Expected: all focused tests and the platform verification script pass. Do not claim the root Maven command passes without explicit datasource variables.

- [x] **Step 3: Complete workflow scope boundary**

`workflow-service` 已完成数据范围闭环：`WorkflowActor` 从 JWT 提取用户、`DataScope` 和 bearer token；请求、档案访问、盘点查询及状态变更均使用 scope-aware Mapper。员工、档案材料和档案版本通过 archive-service 内部授权 API 校验，workflow 不直接读取 archive/identity 数据库；`V4__workflow_data_scope.sql` 保存目标部门与盘点员工/部门快照。兼容旧测试的无 actor service 方法不属于真实 Controller 入口，历史数据的空部门快照按拒绝范围处理。

### 2026-09-20 实施验证记录

- archive-service 全量测试：`32` 个通过；workflow 数据范围测试：`6` 个通过；workflow 持久化测试：`3` 个通过。
- Java 17 重新打包并重启四个 Java 服务，Flyway 实际确认 `workflow_db` 为 V4。
- `bash scripts/verify-platform.sh` 在 `18080-18083` 真实网关链路全部通过，覆盖跨部门人事申请、档案访问、盘点和 OCR 检测框预览拒绝。
