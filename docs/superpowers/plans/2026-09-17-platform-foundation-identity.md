# 平台基础设施与身份组织服务实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立可运行的核心微服务工程骨架，完成统一响应、MySQL 主从数据源路由、Redis 缓存基础能力、网关路由，以及身份与组织服务的登录和 RBAC 最小闭环。

**Architecture:** 使用一个 Maven 多模块后端工程，公共模块只提供协议、数据源路由、缓存和安全基础设施；`gateway-service` 只负责入口路由；`identity-service` 独占 `identity_db`，实现登录、用户、角色、权限、部门和岗位。`archive-service`、`workflow-service` 先建立健康检查和路由占位，后续用独立计划实现业务。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring Cloud Gateway 4.2.x, Spring Security 6.x, MyBatis 3.0.x, MySQL 8-compatible SQL, Flyway, Redis 7, JUnit 5, Maven.

## Global Constraints

- Java 17 LTS；不使用仅支持 Java 21 的 API。
- 服务之间只使用 HTTP API 和业务 ID；禁止跨服务读取数据库或建立跨库物理外键。
- MyBatis 使用显式 Mapper SQL；禁止在业务代码中拼接用户输入的表名、排序字段或 SQL 片段。
- 写事务使用 MySQL 主数据源，`@Transactional(readOnly = true)` 查询使用只读数据源；读己之写强制主库。
- Redis 只保存会话、权限、部门树、字典、短时幂等键和短锁；不保存文件二进制、原图、检测框 PNG 或完整 OCR JSON。
- 所有外部接口统一返回 `code`、`message`、`data`、`traceId`。
- 当前阶段不实现员工档案、OCR 业务、审批和 Vue 页面；这些模块在后续独立计划中实现。
- 每个任务先写测试，再写最小实现；所有任务结束后执行后端全量测试和已有 `ocr-service` 回归测试。

---

### Task 1: 创建 Maven 多模块工程骨架

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/platform-common/platform-common-core/pom.xml`
- Create: `backend/platform-common/platform-common-web/pom.xml`
- Create: `backend/platform-common/platform-common-datasource/pom.xml`
- Create: `backend/platform-common/platform-common-security/pom.xml`
- Create: `backend/gateway-service/pom.xml`
- Create: `backend/identity-service/pom.xml`
- Create: `backend/archive-service/pom.xml`
- Create: `backend/workflow-service/pom.xml`
- Create: `backend/gateway-service/src/main/java/com/hrplatform/gateway/GatewayApplication.java`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/IdentityApplication.java`
- Create: `backend/archive-service/src/main/java/com/hrplatform/archive/ArchiveApplication.java`
- Create: `backend/workflow-service/src/main/java/com/hrplatform/workflow/WorkflowApplication.java`
- Test: `backend/gateway-service/src/test/java/com/hrplatform/gateway/GatewayApplicationTest.java`
- Test: `backend/identity-service/src/test/java/com/hrplatform/identity/IdentityApplicationTest.java`

**Interfaces:**
- Produces four independently executable Spring Boot applications with stable main classes and `/actuator/health` endpoints.
- All services inherit the parent properties `java.version=17`, `project.build.sourceEncoding=UTF-8`, and the Spring Boot dependency management.

- [ ] **Step 1: Write the failing module-load tests**

```java
package com.hrplatform.gateway;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GatewayApplicationTest {
    @Test
    void applicationClassIsAvailable() {
        assertThat(GatewayApplication.class).isNotNull();
    }
}
```

Create the equivalent test for `IdentityApplication.class` under the identity package.

- [ ] **Step 2: Run the test to verify the modules fail before scaffolding**

Run: `mvn -f backend/pom.xml -pl gateway-service,identity-service test`

Expected: FAIL because `backend/pom.xml` and the service classes do not exist.

- [ ] **Step 3: Create the parent POM and module POMs**

The parent POM must import Spring Cloud BOM `org.springframework.cloud:spring-cloud-dependencies:2024.0.2` in `dependencyManagement`, then declare these modules in this order:

```xml
<modules>
  <module>platform-common/platform-common-core</module>
  <module>platform-common/platform-common-web</module>
  <module>platform-common/platform-common-datasource</module>
  <module>platform-common/platform-common-security</module>
  <module>gateway-service</module>
  <module>identity-service</module>
  <module>archive-service</module>
  <module>workflow-service</module>
</modules>
```

Set the parent coordinates to `com.hrplatform:hr-platform:1.0.0-SNAPSHOT`, import `org.springframework.boot:spring-boot-dependencies:3.4.5`, and set `<maven.compiler.release>17</maven.compiler.release>`. The service POMs must use `spring-boot-starter`, `spring-boot-starter-actuator`, and the matching Spring Boot Maven plugin. `identity-service` additionally declares Web, Validation, Security, MyBatis Spring Boot Starter `3.0.4`, MySQL Connector/J, Flyway, JJWT `0.12.6` (`jjwt-api`, `jjwt-impl`, and `jjwt-jackson`), and the common modules. `gateway-service` declares Spring Cloud Gateway and the common web/security modules. Archive and workflow initially declare only Web and Actuator so they can start without unused business dependencies.

- [ ] **Step 4: Add the four application classes**

Each class must have the following structure with its own package:

```java
@SpringBootApplication
public class IdentityApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
```

Use `GatewayApplication`, `ArchiveApplication`, and `WorkflowApplication` for the other modules.

- [ ] **Step 5: Run the module tests**

Run: `mvn -f backend/pom.xml clean test`

Expected: PASS for all four application-context tests and no compilation target above Java 17.

- [ ] **Step 6: Commit the scaffold**

```bash
git add backend
git commit -m "build: add core microservice maven scaffold"
```

If the workspace remains non-git, record the command as unavailable and keep the verified working tree unchanged; do not initialize a repository only for this step.

### Task 2: Add shared response, trace ID, and error handling

**Files:**
- Create: `backend/platform-common/platform-common-core/src/main/java/com/hrplatform/common/api/ApiResponse.java`
- Create: `backend/platform-common/platform-common-core/src/main/java/com/hrplatform/common/api/TraceId.java`
- Create: `backend/platform-common/platform-common-web/src/main/java/com/hrplatform/common/web/TraceIdFilter.java`
- Create: `backend/platform-common/platform-common-web/src/main/java/com/hrplatform/common/web/GlobalExceptionHandler.java`
- Create: `backend/platform-common/platform-common-web/src/main/java/com/hrplatform/common/web/CommonWebConfiguration.java`
- Test: `backend/platform-common/platform-common-web/src/test/java/com/hrplatform/common/web/TraceIdFilterTest.java`
- Test: `backend/platform-common/platform-common-web/src/test/java/com/hrplatform/common/web/GlobalExceptionHandlerTest.java`

**Interfaces:**
- `ApiResponse<T> success(T data, String traceId)` returns `code="0"`.
- `ApiResponse<T> failure(String code, String message, String traceId)` returns `data=null`.
- `TraceId.current()` reads the request trace ID from the request attribute or generates a UUID without hyphens.
- `X-Trace-Id` is accepted from a request only after trimming to 64 ASCII characters; invalid or oversized values are replaced with a generated ID.

- [ ] **Step 1: Write failing tests**

```java
@Test
void filterCreatesTraceIdAndReturnsItInResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(200);

    new TraceIdFilter().doFilter(request, response, chain);

    assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
}
```

Add a handler test asserting that `IllegalArgumentException` maps to `VALIDATION_ERROR` and never exposes a stack trace.

- [ ] **Step 2: Run tests and verify failure**

Run: `mvn -f backend/pom.xml -pl platform-common/platform-common-web -am test`

Expected: FAIL because the response and filter classes are missing.

- [ ] **Step 3: Implement the shared types and filter**

Use a Java record for the response:

```java
public record ApiResponse<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("0", "OK", data, traceId);
    }
}
```

Use `@RestControllerAdvice` for `GlobalExceptionHandler`; map validation exceptions to `VALIDATION_ERROR`, access failures to `FORBIDDEN`, and unknown exceptions to `INTERNAL_ERROR`. Log the trace ID and exception class, but return only the stable public message.

- [ ] **Step 4: Run tests and verify success**

Run: `mvn -f backend/pom.xml -pl platform-common/platform-common-web -am test`

Expected: PASS, including generated and propagated `X-Trace-Id`.

### Task 3: Implement MySQL primary/replica routing

**Files:**
- Create: `backend/platform-common/platform-common-datasource/src/main/java/com/hrplatform/common/datasource/ReadWriteRoutingDataSource.java`
- Create: `backend/platform-common/platform-common-datasource/src/main/java/com/hrplatform/common/datasource/UsePrimary.java`
- Create: `backend/platform-common/platform-common-datasource/src/main/java/com/hrplatform/common/datasource/PrimaryDataSourceAspect.java`
- Create: `backend/platform-common/platform-common-datasource/src/main/java/com/hrplatform/common/datasource/DataSourceProperties.java`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/config/IdentityDataSourceConfiguration.java`
- Create: `backend/identity-service/src/main/resources/application.yml`
- Create: `backend/identity-service/src/main/resources/application-local.yml`
- Test: `backend/platform-common/platform-common-datasource/src/test/java/com/hrplatform/common/datasource/ReadWriteRoutingDataSourceTest.java`
- Test: `backend/identity-service/src/test/java/com/hrplatform/identity/config/IdentityDataSourceConfigurationTest.java`

**Interfaces:**
- `ReadWriteRoutingDataSource` returns key `READ` only when a Spring transaction is active and `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` is true; otherwise it returns `WRITE`.
- `@UsePrimary` forces `WRITE` for the annotated service method, including read-only transactions.
- Configuration properties are `app.datasource.write.url`, `app.datasource.write.username`, `app.datasource.write.password`, and the same `read` fields.

- [ ] **Step 1: Write routing tests**

```java
@Test
void routesToReadWhenTransactionIsReadOnly() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
    try {
        assertThat(new ReadWriteRoutingDataSource().determineCurrentLookupKey()).isEqualTo("READ");
    } finally {
        TransactionSynchronizationManager.clearSynchronization();
    }
}
```

Add a write transaction case and a `@UsePrimary` aspect case. The test must use mock target data sources and must not require a live MySQL instance.

- [ ] **Step 2: Run routing tests and verify failure**

Run: `mvn -f backend/pom.xml -pl platform-common/platform-common-datasource -am test`

Expected: FAIL because the routing data source and annotation do not exist.

- [ ] **Step 3: Implement routing and Spring configuration**

Configure two Hikari data sources with the same pool settings, wrap them in `ReadWriteRoutingDataSource`, and set `lenientFallback=false` for the routing data source. The local profile defaults both URLs to the currently available MySQL endpoint only for routing verification; production/staging configuration must provide distinct primary and replica endpoints. Set the read pool to `readOnly=true`. Do not silently route writes to the read data source.

- [ ] **Step 4: Verify with a MySQL smoke test**

Run: `MYSQL_WRITE_URL=jdbc:mysql://127.0.0.1:3306/identity_db MYSQL_READ_URL=jdbc:mysql://127.0.0.1:3306/identity_db mvn -f backend/pom.xml -pl identity-service -am test`

Expected: Spring context starts and Flyway can connect after the database exists. The test output must show the application profile and both configured datasource roles; it must not print passwords.

### Task 4: Add Redis cache and idempotency primitives

**Files:**
- Create: `backend/platform-common/platform-common-core/src/main/java/com/hrplatform/common/cache/CacheKeys.java`
- Create: `backend/platform-common/platform-common-web/src/main/java/com/hrplatform/common/cache/RedisConfiguration.java`
- Create: `backend/platform-common/platform-common-web/src/main/java/com/hrplatform/common/cache/IdempotencyService.java`
- Create: `backend/platform-common/platform-common-web/src/main/java/com/hrplatform/common/cache/RedisHealthIndicator.java`
- Modify: `backend/identity-service/src/main/resources/application.yml`
- Test: `backend/platform-common/platform-common-web/src/test/java/com/hrplatform/common/cache/IdempotencyServiceTest.java`

**Interfaces:**
- `CacheKeys.permission(long userId)` returns `hr:perm:user:{userId}`.
- `CacheKeys.idempotent(String requestId)` returns `hr:idempotent:{requestId}` and rejects blank IDs.
- `IdempotencyService.claim(String requestId, Duration ttl)` atomically returns `true` only for the first caller; `release(String requestId)` deletes the key.

- [ ] **Step 1: Write the Redis mock test**

```java
@Test
void onlyFirstRequestCanClaimTheSameIdempotencyKey() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(eq("hr:idempotent:req-1"), eq("1"), any(Duration.class)))
        .thenReturn(true, false);

    IdempotencyService service = new IdempotencyService(redis);
    assertThat(service.claim("req-1", Duration.ofMinutes(10))).isTrue();
    assertThat(service.claim("req-1", Duration.ofMinutes(10))).isFalse();
}
```

- [ ] **Step 2: Run the test and verify failure**

Run: `mvn -f backend/pom.xml -pl platform-common/platform-common-web -am test`

Expected: FAIL because the cache primitives do not exist.

- [ ] **Step 3: Implement Redis configuration and health**

Configure `StringRedisSerializer` for keys and Jackson JSON for values. Set `spring.data.redis.host`, `spring.data.redis.port`, and `spring.data.redis.timeout` from environment variables. `IdempotencyService` must use `setIfAbsent` with TTL and never use a non-expiring key. Register a health indicator that reports Redis status without including connection credentials.

- [ ] **Step 4: Run unit tests and Redis smoke test**

Run: `mvn -f backend/pom.xml -pl platform-common/platform-common-web -am test` and `redis-cli ping`.

Expected: all unit tests pass and Redis returns `PONG`.

### Task 5: Create identity database migrations and MyBatis mappings

**Files:**
- Create: `backend/identity-service/src/main/resources/db/migration/V1__identity_schema.sql`
- Create: `backend/identity-service/src/main/resources/db/migration/V2__identity_seed.sql`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/user/SysUser.java`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/user/SysUserMapper.java`
- Create: `backend/identity-service/src/main/resources/mapper/SysUserMapper.xml`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/role/SysRoleMapper.java`
- Create: `backend/identity-service/src/main/resources/mapper/SysRoleMapper.xml`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/org/DepartmentMapper.java`
- Create: `backend/identity-service/src/main/resources/mapper/DepartmentMapper.xml`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/config/IdentitySeedInitializer.java`
- Create: `backend/identity-service/src/main/resources/application-test.yml`
- Test: `backend/identity-service/src/test/java/com/hrplatform/identity/user/SysUserMapperTest.java`

**Interfaces:**
- `SysUserMapper.findEnabledByUsername(String username)` returns one enabled user with `id`, `username`, `passwordHash`, `employeeId`, and `status`.
- `SysRoleMapper.findCodesByUserId(long userId)` returns stable role codes ordered by code.
- `DepartmentMapper.findActiveTree()` returns active departments ordered by `sortNo`, then `id`.

- [ ] **Step 1: Write mapper contract tests**

Use a MySQL integration profile and assert that the seeded `admin` user is returned, disabled users are excluded, role codes are stable, and the department tree contains no inactive nodes. The test must clean its database using Flyway `clean` only in the test profile.

- [ ] **Step 2: Run the mapper tests and verify failure**

Run: `mvn -f backend/pom.xml -pl identity-service -am -Dspring.profiles.active=test test`

Expected: FAIL because migrations and mappers do not exist.

- [ ] **Step 3: Create the V1 schema**

Create these tables with `BIGINT UNSIGNED` IDs, `utf8mb4`, InnoDB, `created_at`, `updated_at`, and status fields: `sys_user`, `sys_role`, `sys_permission`, `sys_user_role`, `sys_role_permission`, `org_department`, `org_position`, and `audit_log`. Add unique indexes for username, role code, permission code, department code, and position code. Do not create an employee table or a cross-database foreign key. Use `password_hash` rather than plaintext passwords.

- [ ] **Step 4: Create deterministic role, permission, and local user seed data**

In `V2__identity_seed.sql`, insert role codes `SYSTEM_ADMIN`, `HR_ADMIN`, `DEPT_MANAGER`, and `EMPLOYEE`; insert permission codes for authentication, organization read/write, and audit read; insert role-permission links; and insert a root `总部` department with a deterministic code. Do not put a user password in SQL. `IdentitySeedInitializer` runs only under `local` or `test`, checks whether `admin` exists, and inserts an active admin user using a BCrypt hash generated from the runtime property `identity.seed.admin-password`. Set that property to `test-password` only in `application-test.yml`; production must provision an already-hashed value through an operator-managed secret and must not enable this initializer.

- [ ] **Step 5: Add explicit MyBatis SQL**

`SysUserMapper.xml` must use a parameterized query:

```xml
<select id="findEnabledByUsername" resultType="com.hrplatform.identity.user.SysUser">
  SELECT id, username, password_hash AS passwordHash, employee_id AS employeeId, status
  FROM sys_user
  WHERE username = #{username} AND status = 'ACTIVE' AND deleted_at IS NULL
</select>
```

Add equivalent explicit selects for role codes and department tree. Configure `@MapperScan("com.hrplatform.identity")` and `mybatis.mapper-locations=classpath:/mapper/*.xml`.

- [ ] **Step 6: Run mapper tests and verify success**

Run: `mvn -f backend/pom.xml -pl identity-service -am -Dspring.profiles.active=test test`.

Expected: Flyway applies V1 and V2, mapper tests pass, and no SQL reads another service database.

### Task 6: Implement JWT login, current-user, and department APIs

**Files:**
- Create: `backend/platform-common/platform-common-security/src/main/java/com/hrplatform/common/security/JwtService.java`
- Create: `backend/platform-common/platform-common-security/src/main/java/com/hrplatform/common/security/JwtAuthenticationFilter.java`
- Create: `backend/platform-common/platform-common-security/src/main/java/com/hrplatform/common/security/SecurityConfiguration.java`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/auth/AuthController.java`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/auth/AuthService.java`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/auth/LoginRequest.java`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/auth/LoginData.java`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/org/DepartmentController.java`
- Create: `backend/identity-service/src/main/java/com/hrplatform/identity/org/DepartmentService.java`
- Create: `backend/identity-service/src/test/java/com/hrplatform/identity/auth/AuthControllerTest.java`
- Create: `backend/identity-service/src/test/java/com/hrplatform/identity/org/DepartmentControllerTest.java`

**Interfaces:**
- `POST /api/auth/login` accepts `{ "username": "admin", "password": "..." }` and returns `ApiResponse<LoginData>`.
- `GET /api/auth/me` requires a valid JWT and returns user ID, username, role codes, permission codes, and employee ID.
- `GET /api/departments/tree` requires `ORG_READ` and returns active department nodes.
- JWT claims: `sub`, `username`, `roles`, `permissions`, `employeeId`, `iat`, `exp`, and `jti`. Access token lifetime is 30 minutes, configured by `security.jwt.access-token-ttl`.

- [ ] **Step 1: Write controller tests**

Mock `AuthService` and assert:

```java
mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.code").value("0"))
    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
```

Add cases for bad credentials (`AUTH_INVALID_CREDENTIALS`), missing fields (`VALIDATION_ERROR`), missing JWT (`AUTH_REQUIRED`), and missing `ORG_READ` (`FORBIDDEN`).

- [ ] **Step 2: Run tests and verify failure**

Run: `mvn -f backend/pom.xml -pl identity-service -am test`.

Expected: FAIL because authentication services and controllers are missing.

- [ ] **Step 3: Implement BCrypt login and JWT issuance**

Use `PasswordEncoder.matches(rawPassword, passwordHash)`; never log the raw password. Load roles and permissions from MyBatis, issue a signed JWT with a secret from `SECURITY_JWT_SECRET`, and return only the access token and expiration timestamp. Do not put sensitive employee fields in JWT claims.

- [ ] **Step 4: Implement JWT validation and authorization**

The filter reads `Authorization: Bearer <token>`, validates signature, expiration, and issuer, then creates an authenticated principal. `SecurityConfiguration` permits `/actuator/health` and `/api/auth/login`, authenticates all other API routes, and maps permission claims to `GrantedAuthority` with the `PERM_` prefix.

- [ ] **Step 5: Implement current-user and department endpoints**

`/api/auth/me` reads the authenticated principal and returns the non-secret claims. `DepartmentController` uses `@PreAuthorize("hasAuthority('PERM_ORG_READ')")` and `@Transactional(readOnly = true)` so the department tree goes through the read data source and Redis cache can be added without changing the controller contract.

- [ ] **Step 6: Run API tests and verify success**

Run: `mvn -f backend/pom.xml -pl identity-service -am test`.

Expected: all authentication, authorization, mapper, and application-context tests pass; response bodies contain `code`, `message`, `data`, and `traceId`.

### Task 7: Configure gateway routes and service health endpoints

**Files:**
- Create: `backend/gateway-service/src/main/resources/application.yml`
- Create: `backend/gateway-service/src/main/java/com/hrplatform/gateway/GatewaySecurityConfiguration.java`
- Create: `backend/gateway-service/src/test/java/com/hrplatform/gateway/GatewayRouteConfigurationTest.java`
- Modify: `backend/archive-service/src/main/resources/application.yml`
- Modify: `backend/workflow-service/src/main/resources/application.yml`

**Interfaces:**
- Gateway routes `/api/auth/**`, `/api/departments/**` to `identity-service`; `/api/employees/**` and `/api/archive/**` to `archive-service`; `/api/hr-requests/**`, `/api/approvals/**`, `/api/archive-access-applications/**`, `/api/archive-uses/**`, and `/api/inventory-tasks/**` to `workflow-service`.
- Gateway permits `GET /actuator/health` and `POST /api/auth/login`; all other routes require JWT validation or are passed to the downstream service for final authorization.
- Downstream services expose `GET /actuator/health` on internal ports only.

- [ ] **Step 1: Write route configuration tests**

Load the gateway application context with service URL properties and assert that the route IDs and path predicates exist. Assert that no route targets a database URL or the OCR SQLite directory.

- [ ] **Step 2: Run the route test and verify failure**

Run: `mvn -f backend/pom.xml -pl gateway-service -am test`.

Expected: FAIL because route configuration does not exist.

- [ ] **Step 3: Add routes and internal service configuration**

Use environment variables `IDENTITY_SERVICE_URL`, `ARCHIVE_SERVICE_URL`, and `WORKFLOW_SERVICE_URL` with local defaults `http://127.0.0.1:8081`, `http://127.0.0.1:8082`, and `http://127.0.0.1:8083`. Add a gateway global filter that forwards `X-Trace-Id` and removes any externally supplied internal-only headers.

- [ ] **Step 4: Run route and context tests**

Run: `mvn -f backend/pom.xml -pl gateway-service,archive-service,workflow-service -am test`.

Expected: all three applications load and their health endpoints are available when started with their local profiles.

### Task 8: Add local infrastructure configuration and verification scripts

**Files:**
- Create: `infra/docker-compose.yml`
- Create: `infra/mysql/primary/conf.d/server.cnf`
- Create: `infra/mysql/replica/conf.d/server.cnf`
- Create: `infra/mysql/primary/init/01-create-databases.sql`
- Create: `infra/mysql/replica/init/01-replica-init.sql`
- Create: `infra/.env.example`
- Create: `scripts/verify-platform.sh`
- Create: `backend/README.md`

**Interfaces:**
- `docker compose -f infra/docker-compose.yml up -d` starts Redis, MinIO, MySQL primary, and MySQL replica with stable service names.
- `scripts/verify-platform.sh` checks MySQL primary, Redis, MinIO, the gateway health endpoint, and identity health endpoint; it exits nonzero on the first failed check.

- [ ] **Step 1: Write the verification script assertions**

The script must assert `redis-cli -h "$REDIS_HOST" ping` equals `PONG`, both MySQL endpoints accept a connection, and `curl --fail` succeeds for the two health URLs. It must not echo passwords.

- [ ] **Step 2: Add Compose services and documented environment**

Use named volumes, non-root application users where supported, health checks, and sample values in `.env.example`. Keep database credentials outside tracked files. Configure the primary with binary logging and GTID, create a replication user in the primary init script, configure the replica with `read_only=ON` and `super_read_only=ON`, then run `CHANGE REPLICATION SOURCE TO ...; START REPLICA;` from `01-replica-init.sql` after the primary health check succeeds. The script must verify `SHOW REPLICA STATUS` reports an active SQL thread and IO thread before the Compose check passes.

- [ ] **Step 3: Run infrastructure verification**

Run: `docker compose -f infra/docker-compose.yml config`, then `docker compose -f infra/docker-compose.yml up -d`, then `bash scripts/verify-platform.sh`.

Expected: Compose configuration is valid, Redis returns `PONG`, MySQL primary and replica accept connections, and service health endpoints respond. If Docker is unavailable, run the script against the existing local MySQL/Redis processes and record the missing container check without weakening application tests.

### Task 9: Full regression and implementation handoff

**Files:**
- Modify: `docs/方案设计.md` with the verified implementation baseline and actual local connection assumptions.
- Modify: `docs/superpowers/specs/2026-09-17-employee-archive-platform-design.md` with the completed phase status.
- Create: `backend/README.md` test and startup instructions if Task 8 did not create it.

- [ ] **Step 1: Run backend verification**

Run: `mvn -f backend/pom.xml clean test`.

Expected: all common-module, gateway, identity, archive, and workflow tests pass.

- [ ] **Step 2: Run OCR regression**

Run from the OCR service directory: `.venv/bin/pytest -q`.

Expected: the existing OCR suite remains green with 30 tests passed, and the business scaffold has not changed OCR endpoint contracts.

- [ ] **Step 3: Verify API surface**

Start gateway and identity with local profiles, then run:

```bash
curl --fail http://127.0.0.1:8080/actuator/health
curl --fail http://127.0.0.1:8081/actuator/health
LOGIN_PASSWORD="${IDENTITY_ADMIN_PASSWORD:-test-password}"
curl --fail -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"${LOGIN_PASSWORD}\"}"
```

Expected: both health checks return `UP`; login returns `code="0"`, a non-empty access token, and a trace ID.

- [ ] **Step 4: Review the diff and hand off the next plan**

Review every new file for secrets, cross-database SQL, unbounded Redis keys, missing trace IDs, and routes that bypass gateway authorization. After this phase passes, create the separate implementation plan for `archive-service` employee/archive/OCR integration before writing those business modules.
