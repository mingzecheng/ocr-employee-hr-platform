# 01 · 后端模块事实清单

> 来源：`server/src/main/java/com/hrplatform/` 全量代码阅读。
> 主类：`com.hrplatform.PlatformApplication`（`@SpringBootApplication` + `@EnableScheduling` + `@MapperScan(basePackages="com.hrplatform", annotationClass=Mapper.class)`）。
> 说明：本文件只列"代码里真实存在的类与方法签名"，不做评价。所有路径均相对项目根。

---

## 1. 模块（package）总览

| 包 | 中文职责 | 关键 Controller | 关键 Service | 关键 Mapper |
|---|---|---|---|---|
| `auth` | 登录/注册、系统用户管理 | `AuthController`, `AdminUserController` | `AuthService`, `AdminUserService` | `SysUserMapper` |
| `organization` | 部门树、岗位 | `OrganizationController` | `OrganizationService` | `OrganizationMapper` |
| `employee` | 员工档案主档、状态历史 | `EmployeeController` | `EmployeeService` | `EmployeeMapper` |
| `archive` | 档案文档、版本、MinIO 存储、下载 | `ArchiveController` | `ArchiveService`, `ArchiveCatalog`, `MinioObjectStorage`（实现 `ObjectStorage`） | `ArchiveMapper` |
| `ocr` | OCR 触发、结果查询、字段人工修订、检测预览 | `OcrController`（挂在 `/api/archive` 下） | `OcrService`, `HttpOcrClient`（实现 `OcrClient`） | `OcrBindingMapper`, `BusinessFieldMapper` |
| `workflow` | 人事申请（入职/调动/请假/离职）、审批、待办 | `RequestController`, `TodoController` | `RequestService`, `RequestStateMachine`, `TodoService`, `EmployeeChangeService`, `EmployeeChangeScheduler` | `RequestMapper`, `TodoMapper` |
| `access` | 档案借用/访问授权申请、使用/归还/异常处理 | `ArchiveAccessController` | `ArchiveAccessService`, `ArchiveAccessStateMachine`, `ArchiveAccessExpiryScheduler`, `ArchiveReadAuthorizationPolicy` | `AccessMapper` |
| `inventory` | 档案盘点任务、盘点项、差异异常闭环 | `InventoryController` | `InventoryService` | `InventoryMapper` |
| `contract` | 员工劳动合同台账 | `ContractController` | `ContractService` | `ContractMapper` |
| `statistics` | 人事统计概览与报表 | `StatisticsController` | `StatisticsService` | `StatisticsMapper` |
| `audit` | 操作日志查询 | `AuditController` | `OperationLogService` | `OperationLogMapper` |
| `common/security` | JWT、RBAC、数据范围 | — | `JwtService`, `JwtAuthenticationFilter`, `SecurityConfiguration` | — |
| `common/web` | 全局异常、响应包装、TraceId | — | `GlobalExceptionHandler`, `TraceIdFilter` | — |
| `common/config` | 生产环境启动校验、管理员引导 | — | `ProductionConfigurationValidator`, `ProductionAdminBootstrap` | — |

---

## 2. Controller 路由清单（按类聚合）

> 所有 Controller 统一返回 `ApiResponse<T>`（`{code, message, data, traceId}`，成功 `code="0"`）。
> 权限注解均为方法级 `@PreAuthorize`（`@EnableMethodSecurity` 在 `SecurityConfiguration` 开启）。

### 2.1 `AuthController` — `/api/auth`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/login` | 公开 | 账号密码登录，返回 `AuthResponse`（accessToken + user） |
| POST | `/register` | 公开 | 员工自助注册 |
| GET | `/me` | 已登录 | 返回当前 `JwtPrincipal` |

### 2.2 `AdminUserController` — `/api/admin/users`（类级 `@PreAuthorize("hasRole('SYSTEM_ADMIN')")`）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `` | 分页查询系统用户 |
| GET | `/roles` | 返回可选角色列表 `AdminRoleOption` |
| POST | `` | 创建系统用户 |
| PUT | `/{id}` | 更新系统用户 |

### 2.3 `OrganizationController` — `/api/departments`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/tree` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN/SYSTEM_ADMIN | 部门树 |
| GET | `/{departmentId}/positions` | 同上 | 部门下岗位选项 |
| GET | `/admin` | HR_ADMIN/SYSTEM_ADMIN | 部门管理视图 |
| GET | `/{departmentId}/positions/admin` | HR_ADMIN/SYSTEM_ADMIN | 岗位管理视图 |
| POST | `` | HR_ADMIN/SYSTEM_ADMIN | 新建部门 |
| PUT | `/{departmentId}` | HR_ADMIN/SYSTEM_ADMIN | 更新部门 |
| POST | `/{departmentId}/positions` | HR_ADMIN/SYSTEM_ADMIN | 新建岗位 |
| PUT | `/{departmentId}/positions/{positionId}` | HR_ADMIN/SYSTEM_ADMIN | 更新岗位 |

### 2.4 `EmployeeController` — `/api/employees`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `` | HR_ADMIN | 创建员工主档 |
| PUT | `/{employeeId}/profile` | HR_ADMIN | 更新员工档案 |
| GET | `/{employeeId}` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 员工详情（HR 可见完整手机号） |
| GET | `/{employeeId}/status-history` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 员工状态变更历史分页 |
| GET | `` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 员工列表（keyword/departmentId/status 过滤，分页） |

### 2.5 `ArchiveController` — `/api/archive`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/employees/{employeeId}/documents` | HR_ADMIN | 上传档案材料（multipart：documentType + file） |
| GET | `/employees/{employeeId}/documents` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 员工档案文档列表 |
| GET | `/documents/{documentId}/versions` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 文档版本列表 |
| GET | `/versions/{versionId}/download` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 下载版本文件（inline 流） |

### 2.6 `OcrController` — `/api/archive`（与 ArchiveController 同前缀）
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/versions/{versionId}/ocr` | HR_ADMIN | 触发一次 OCR 识别（同步等待结果） |
| GET | `/versions/{versionId}/ocr-result` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 查询某版本的 OCR 结果 |
| GET | `/ocr-bindings/{bindingId}/preview` 或 `/detection-preview` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 返回检测框效果图 PNG |
| PUT | `/ocr-bindings/{bindingId}/fields/{fieldCode}` | HR_ADMIN | 人工修订某字段（value + reason） |

> 注：触发 OCR 前会调用 `ArchiveReadAuthorization.allowsRead(versionId, createdBy, dataScope)`，无授权则抛 `OcrBindingNotFoundException("档案版本不存在或无有效访问授权")`。

### 2.7 `RequestController` — `/api/hr-requests`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 创建人事申请（ONBOARDING/TRANSFER/LEAVE/OFFBOARDING） |
| POST | `/{id}/submit` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 草稿提交 |
| POST | `/{id}/cancel` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 取消草稿 |
| POST | `/{id}/approve` | DEPT_MANAGER/HR_ADMIN | 审批通过（按当前节点校验角色） |
| POST | `/{id}/reject` | DEPT_MANAGER/HR_ADMIN | 审批驳回 |
| PUT | `/{id}/handover` | HR_ADMIN | 离职交接完成（仅离职流程） |
| GET | `` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 申请列表（type/status/employeeId 过滤） |
| GET | `/{id}` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 申请详情（含 display 上下文、审批记录） |

### 2.8 `TodoController` — `/api/todos`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 当前用户待办聚合（limit 默认 50） |

### 2.9 `ArchiveAccessController` — `/api/archive-access`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 创建借用申请草稿 |
| POST | `/{id}/submit` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 提交申请 |
| POST | `/{id}/approve` | HR_ADMIN | HR 审批通过 |
| POST | `/{id}/reject` | HR_ADMIN | HR 驳回（reason 必填） |
| POST | `/{id}/use` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 标记开始使用 |
| POST | `/{id}/return` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 归还（abnormal/remark/handoverToEmployeeId） |
| POST | `/{id}/resolve` | HR_ADMIN | 异常归还后 HR 闭环 |
| GET | `/{id}` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 申请详情（含 items、uses、resolutions、decisions） |
| GET | `` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 申请列表（status/applicantId 过滤，分页） |

### 2.10 `InventoryController` — `/api/inventory-tasks`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `` | DEPT_MANAGER/HR_ADMIN | 创建盘点任务（传 versionIds 集合） |
| GET | `` | DEPT_MANAGER/HR_ADMIN | 盘点任务分页列表 |
| POST | `/{id}/start` | DEPT_MANAGER/HR_ADMIN | 启动盘点 |
| GET | `/{id}/items` | DEPT_MANAGER/HR_ADMIN | 任务下盘点项 |
| GET | `/{id}/items/{itemId}/versions` | DEPT_MANAGER/HR_ADMIN | 某盘点项可选的档案版本列表 |
| POST | `/{id}/items/{itemId}/check` | DEPT_MANAGER/HR_ADMIN | 提交一项盘点结果（actualVersionId 等） |
| POST | `/{id}/complete` | DEPT_MANAGER/HR_ADMIN | 完成盘点任务 |
| POST | `/{id}/items/{itemId}/resolve` | DEPT_MANAGER/HR_ADMIN | 闭环某盘点项差异 |

### 2.11 `ContractController` — `/api/contracts`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN | 合同列表（employeeId/status/renewalStatus 过滤） |
| POST | `` | HR_ADMIN | 新建合同 |
| PUT | `/{id}/renewal-status` | HR_ADMIN | 标记"不再续签" |

### 2.12 `StatisticsController` — `/api/statistics`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/overview` | 全角色（SYSTEM_ADMIN 强制 ALL 范围） | 概览指标（员工数、档案完整度、待办、OCR 统计等） |
| GET | `/report` | 全角色 | 月报（月度趋势 + 部门分布 + 流程周期） |

### 2.13 `AuditController` — `/api/operation-logs`
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `` | HR_ADMIN/SYSTEM_ADMIN | 操作日志分页（operatorId/action/objectType/result/from/to） |

---

## 3. 核心 Service 公开方法签名

> 只列业务入口方法，省略私有 helper。所有写方法基本都带 `@Transactional`（只读方法 `@Transactional(readOnly=true)`）。

### 3.1 `AuthService`
- `AuthResponse login(LoginRequest request)`
- `@Transactional AuthResponse register(RegisterRequest request)`

### 3.2 `AdminUserService`
- `AdminUserPage list(int page, int pageSize)`
- `List<AdminRoleOption> roles()`
- `@Transactional AdminUserView create(AdminUserCreateRequest input, Long operatorId)`
- `@Transactional AdminUserView update(Long id, AdminUserUpdateRequest input, Long operatorId)`

### 3.3 `OrganizationService`
- `List<DepartmentNode> departmentTree()`
- `List<PositionOption> positions(Long departmentId)`
- `List<DepartmentAdminView> adminDepartments()`
- `List<PositionAdminView> adminPositions(Long departmentId)`
- `@Transactional DepartmentAdminView createDepartment(DepartmentMutationRequest input, Long operatorId)`
- `@Transactional DepartmentAdminView updateDepartment(Long id, DepartmentMutationRequest input, Long operatorId)`
- `@Transactional PositionAdminView createPosition(Long departmentId, PositionMutationRequest input, Long operatorId)`
- `@Transactional PositionAdminView updatePosition(Long departmentId, PositionMutationRequest input, Long operatorId)`

### 3.4 `EmployeeService`
- `@Transactional Employee create(EmployeeCreateRequest request, Long operatorId)`
- `Employee getRequired(Long employeeId, DataScope scope)`
- `@Transactional Employee updateProfile(Long employeeId, EmployeeProfileUpdateRequest request, Long operatorId)`
- `EmployeePage list(String keyword, Long departmentId, EmployeeStatus status, int page, int pageSize, DataScope scope)`
- `EmployeeStatusHistoryPage listStatusHistory(Long employeeId, int page, int pageSize, DataScope scope)`

### 3.5 `ArchiveService`
- `@Transactional ArchiveVersion createVersion(Long employeeId, String documentType, String originalName, String contentType, byte[] content, DataScope scope, Long operatorId)`
- `ArchiveDownload download(Long versionId, DataScope scope)`
- `ArchiveDocumentPage listDocuments(Long employeeId, int page, int pageSize, DataScope scope)`
- `ArchiveVersionPage listVersions(Long documentId, int page, int pageSize, DataScope scope)`

### 3.6 `OcrService`
- `@Transactional OcrBinding trigger(OcrTrigger trigger)`（旧入口，内部委托 `triggerResult`）
- `@Transactional OcrResult triggerResult(OcrTrigger trigger)`（同步调用 `HttpOcrClient.create`，等待识别完成后落库 `ocr_binding` + 字段）
- `OcrResult getResult(Long versionId, DataScope scope)`
- `byte[] preview(Long bindingId, DataScope scope)`
- `@Transactional OcrResult reviseField(Long bindingId, String fieldCode, String value, String reason, Long operatorId, DataScope scope)`
- `OcrTaskData getTask(String taskId)`

### 3.7 `RequestService`（人事申请）
- `@Transactional HrRequest create(RequestCreateRequest request, RequestActor actor)`
- `@Transactional HrRequest submit(Long id, RequestActor actor)`
- `@Transactional HrRequest cancel(Long id, RequestActor actor)`
- `@Transactional HrRequest approve(Long id, RequestActor actor, String comment)`
- `@Transactional HrRequest reject(Long id, RequestActor actor, String reason)`
- `@Transactional HrRequest completeHandover(Long id, RequestActor actor)`
- `HrRequest get(Long id, RequestActor actor)`
- `RequestDetail detail(Long id, RequestActor actor)`
- `RequestPage list(String requestType, String status, Long employeeId, int page, int pageSize, RequestActor actor)`
- `@Transactional boolean applyDueEmployeeChange(Long id)`

### 3.8 `EmployeeChangeService`（员工异动生效）
- `@Transactional LocalDateTime applyApprovedChange(HrRequest request, Long operatorId)`
- `void lockEmployeeForApproval(HrRequest request)`
- `@Transactional boolean applyDueEmployeeChange(Long requestId)`

### 3.9 `TodoService`
- `TodoData list(RequestActor actor, int limit)`

### 3.10 `ArchiveAccessService`（档案借用）
- `@Transactional ArchiveAccessApplication create(AccessCreateRequest request, AccessActor actor)`
- `@Transactional ArchiveAccessApplication submit(Long id, AccessActor actor)`
- `@Transactional ArchiveAccessApplication approve(Long id, AccessActor actor)`
- `@Transactional ArchiveAccessApplication reject(Long id, String reason, AccessActor actor)`
- `@Transactional ArchiveAccessApplication use(Long id, AccessActor actor)`
- `@Transactional ArchiveAccessApplication returnAccess(Long id, AccessActor actor, boolean abnormal, String remark, Long handoverToEmployeeId)`
- `@Transactional ArchiveAccessApplication resolveAbnormal(Long id, AccessActor actor, String remark)`
- `@Transactional int expireOverdueInUseApplications()`
- `@Transactional int expireUnusedApprovedApplications()`
- `ArchiveAccessDetail detail(Long id, AccessActor actor)`
- `AccessPage list(String status, Long applicantId, int page, int pageSize, AccessActor actor)`

### 3.11 `InventoryService`（盘点）
- `@Transactional InventoryTask create(InventoryCreateRequest request, InventoryActor actor)`
- `InventoryPage list(int page, int pageSize, InventoryActor actor)`
- `@Transactional InventoryTask start(Long id, InventoryActor actor)`
- `List<InventoryItem> listItems(Long taskId, InventoryActor actor)`
- `List<InventoryVersionOption> availableVersions(Long taskId, Long itemId, InventoryActor actor)`
- `@Transactional InventoryItem checkItem(Long taskId, Long itemId, InventoryCheckRequest request, InventoryActor actor)`
- `@Transactional InventoryTask complete(Long id, InventoryActor actor)`
- `@Transactional InventoryItem resolve(Long taskId, Long itemId, String resolution, InventoryActor actor)`

### 3.12 `ContractService`
- `@Transactional ContractRecord create(ContractCreateRequest request, DataScope scope, Long operatorId)`
- `ContractPage list(Long employeeId, String status, String renewalStatus, int page, int pageSize, DataScope scope)`
- `@Transactional ContractRecord markNotRenewing(Long id, DataScope scope, Long operatorId)`

### 3.13 `StatisticsService`
- `StatisticsOverview overview(DataScope scope, LocalDate from, LocalDate to)`
- `StatisticsReport report(DataScope scope, LocalDate from, LocalDate to)`

### 3.14 `OperationLogService`（审计）
- `void record(Long operatorId, String action, String objectType, Long objectId, String result)`
- `void record(Long operatorId, String action, String objectType, Long objectId, String result, String beforeSummary, String afterSummary)`
- **重要事实**：操作日志是 Service 层**手动调用**（13 个类调用它），**没有 AOP 切面**（grep `@Aspect/@Around` 在 main/java 下零命中）。

---

## 4. Mapper 接口清单（MyBatis，`@Mapper`）

| Mapper | 所属模块 | 对应主表 |
|---|---|---|
| `SysUserMapper` | auth | `sys_user`, `sys_user_role`, `sys_role` |
| `OrganizationMapper` | organization | `org_department`, `org_position` |
| `EmployeeMapper` | employee | `employee`, `employee_status_history` |
| `ArchiveMapper` | archive | `employee_archive`, `archive_document`, `archive_version`, `file_object` |
| `OcrBindingMapper` | ocr | `ocr_binding` |
| `BusinessFieldMapper` | ocr | `business_field`（运行时字段字典，非 Flyway 建表，见 02） |
| `RequestMapper` | workflow | `hr_request`, `onboarding_detail`, `transfer_detail`, `leave_request_detail`, `offboarding_detail`, `approval_record` |
| `TodoMapper` | workflow | （只读聚合视图，不直接写表） |
| `AccessMapper` | access | `archive_access_application`, `archive_access_item`, `archive_use_record`, `archive_access_resolution`, `archive_access_decision` |
| `InventoryMapper` | inventory | `inventory_task`, `inventory_item`, `exception_record` |
| `ContractMapper` | contract | `employee_contract` |
| `StatisticsMapper` | statistics | （多表只读聚合 SQL） |
| `OperationLogMapper` | audit | `operation_log` |

> XML 映射位置：`server/src/main/resources/mapper/**/*.xml`（`mybatis.mapper-locations=classpath:/mapper/**/*.xml`）。

---

## 5. `common/security` 关键类

| 类 | 全限定名 | 职责 |
|---|---|---|
| `SecurityConfiguration` | `com.hrplatform.common.security.SecurityConfiguration` | `@Configuration @EnableMethodSecurity`；注册 `JwtService`、`PasswordEncoder`（BCrypt）、`SecurityFilterChain`；放行 `/actuator/health`、`POST /api/auth/login`、`POST /api/auth/register`；其余 `.authenticated()`；无状态 Session；过滤器链：`TraceIdFilter` → `JwtAuthenticationFilter` |
| `JwtAuthenticationFilter` | `...security.JwtAuthenticationFilter` | `OncePerRequestFilter`；解析 `Authorization: Bearer <token>`；`liveValidation=true` 时回查 `sys_user` 是否 enabled 并重载角色；构造 `JwtPrincipal` 放入 `SecurityContextHolder` |
| `JwtService` | `...security.JwtService` | 签发/解析 JWT（jjwt）；claims：`sub=userId`、`employeeId`、`departmentId`、`roles`；默认过期 `PT8H`（`platform.jwt.expiration`） |
| `JwtPrincipal` | `...security.JwtPrincipal` | `record(Long userId, Long employeeId, Long departmentId, Set<String> roles)`；`dataScope()` 工厂方法 |
| `DataScope` | `...security.DataScope` | `record(Type type, Long userId, Long employeeId, Long departmentId)`；`Type ∈ {NONE, EMPLOYEE, DEPARTMENT, ALL}`；规则：HR_ADMIN→ALL；DEPT_MANAGER+departmentId→DEPARTMENT；EMPLOYEE+employeeId→EMPLOYEE；否则 NONE |
| `JwtAuthenticationException` | `...security.JwtAuthenticationException` | 认证失败异常 |

**角色常量**（来自 `sys_role` 表 V1 seed 与 `@PreAuthorize` 使用）：
`EMPLOYEE`（员工/业务经办）、`DEPT_MANAGER`（部门负责人）、`HR_ADMIN`（人事管理员）、`SYSTEM_ADMIN`（系统管理员）。

**权限模型**：方法级 `hasRole/hasAnyRole` + 业务层 `DataScope.canAccess(targetEmployeeId, targetDepartmentId)` 二次过滤。
**未发现**独立的 `PermissionEvaluator` bean 或 `sys_permission` 表被代码使用（`sys_permission` 与 `sys_role_permission` 表在 V1 建好但代码未读）。

---

## 6. `common/web` 关键类

| 类 | 职责 |
|---|---|
| `ApiResponse<T>` | `record(String code, String message, T data, String traceId)`；成功 `code="0"`，失败由 `GlobalExceptionHandler` 填充错误码 |
| `GlobalExceptionHandler` | `@RestControllerAdvice`；映射异常 → HTTP 状态码 + 错误码：`MethodArgumentNotValidException/HttpMessageNotReadableException/IllegalArgumentException`→400 `VALIDATION_ERROR`；`InvalidCredentialsException`→401 `UNAUTHORIZED`；`AccessDeniedException`→403 `FORBIDDEN`；`DataScopeDeniedException/ArchiveReadDeniedException`→403 `FORBIDDEN`；`OcrBindingNotFoundException/OcrFieldNotFoundException/OcrPreviewMissingException`→404 `NOT_FOUND`；`RequestStateException/ArchiveAccessStateException/InventoryStateException/OrganizationStateException/AccountAdminStateException`→409 `STATE_CONFLICT`；兜底 500 `INTERNAL_ERROR`。失败时通过 `OperationLogService.record(operatorId,"REQUEST_FAILED","API_REQUEST",null,"FAILED",...)` 落审计。 |
| `TraceIdFilter` | `OncePerRequestFilter`；从 `X-Trace-Id` 头读取或生成 UUID；写入 MDC 与响应头 |

---

## 7. `common/config` 关键类

| 类 | Profile | 职责 |
|---|---|---|
| `ProductionConfigurationValidator` | `production` | 启动时校验 `JWT_SECRET/MYSQL_PASSWORD/OCR_INTERNAL_TOKEN/MINIO_ACCESS_KEY/MINIO_SECRET_KEY` 不得使用默认值；`PLATFORM_DEMO_ACCOUNTS_ENABLED` 必须为 false；不满足直接抛 `IllegalStateException` 拒绝启动 |
| `ProductionAdminBootstrap` | `production` | `ApplicationRunner`；若系统中无 enabled 的 SYSTEM_ADMIN，则用 `BOOTSTRAP_ADMIN_USERNAME/PASSWORD` 创建首个管理员并写审计日志 |

> **没有** `WebMvcConfig`/`CorsConfig`/`OpenApiConfig`/`MyBatisConfig` 类。CORS 由前端 Vite dev proxy 处理（见 06），生产环境同源部署。

---

## 8. 定时任务（`@EnableScheduling` 在主类）

| 类 | 调度表达式 | 动作 |
|---|---|---|
| `ArchiveAccessExpiryScheduler.markOverdueAccessAsAbnormal()` | `fixedDelay=${hr.archive-access.expiry-delay-ms:60000}`（60s） | 调 `expireOverdueInUseApplications()`（已超 `due_at` 仍在 IN_USE 的申请标记异常）+ `expireUnusedApprovedApplications()`（已批准但未在 `start_at` 后使用的回收） |
| `EmployeeChangeScheduler.applyDueEmployeeChanges()` | `cron=${hr.workflow.employee-change-cron:0 * * * * *}`（每分钟），时区 Asia/Shanghai | 查 `listDueEmployeeChangeIds(today, 100)`，逐个 `requestService.applyDueEmployeeChange(requestId)`；失败写审计 |

---

## 9. 关键事实备注

1. **OCR 调用是同步 HTTP**：`HttpOcrClient` 用 `RestClient`（connect 3s / read 30s），`OcrService.triggerResult` 在事务内同步等待 FastAPI 返回。
2. **Redis 在业务代码中零使用**：grep `RedisTemplate/StringRedisTemplate/@Cacheable/RedisConnectionFactory` 在 `server/src/main/java` 下无任何命中；`application.yml` 仅配置了 `spring.data.redis.host/port`，Spring Boot 自动装配会创建连接工厂但无 bean 被注入。
3. **操作日志无 AOP 切面**：靠每个 Service 手动调用 `OperationLogService.record(...)`。
4. **`sys_permission` / `sys_role_permission` 表未被代码读取**：权限判断完全靠 `@PreAuthorize` 字符串 + `DataScope`。
5. **演示账号开关**：`platform.demo-accounts-enabled`（默认 true）；V16 迁移在该开关为 false 时禁用所有 `demo-*` 账号与 `DEMO-*` 部门/岗位。
