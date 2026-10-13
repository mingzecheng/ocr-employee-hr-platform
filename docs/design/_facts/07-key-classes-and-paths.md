# 07 · 关键代码锚点速查

> 后续设计文档（00–06）会反复引用本文件中的"代码锚点"。所有路径相对项目根目录。

---

## 1. 后端核心 Service 全限定名

| 模块 | Service 全限定名 | 文件路径 |
|---|---|---|
| 认证 | `com.hrplatform.auth.AuthService` | `server/src/main/java/com/hrplatform/auth/AuthService.java` |
| 用户管理 | `com.hrplatform.auth.AdminUserService` | `.../auth/AdminUserService.java` |
| 组织岗位 | `com.hrplatform.organization.OrganizationService` | `.../organization/OrganizationService.java` |
| 员工主档 | `com.hrplatform.employee.EmployeeService` | `.../employee/EmployeeService.java` |
| 档案文档/版本 | `com.hrplatform.archive.ArchiveService` | `.../archive/ArchiveService.java` |
| 档案目录（读授权） | `com.hrplatform.archive.ArchiveCatalog` / `com.hrplatform.archive.ArchiveReadAuthorization` | `.../archive/ArchiveCatalog.java` / `ArchiveReadAuthorization.java` |
| MinIO 存储 | `com.hrplatform.archive.MinioObjectStorage`（实现 `ObjectStorage`） | `.../archive/MinioObjectStorage.java` |
| OCR 编排 | `com.hrplatform.ocr.OcrService` | `.../ocr/OcrService.java` |
| OCR HTTP 客户端 | `com.hrplatform.ocr.HttpOcrClient`（实现 `OcrClient`） | `.../ocr/HttpOcrClient.java` |
| 人事申请 | `com.hrplatform.workflow.RequestService` | `.../workflow/RequestService.java` |
| 员工异动生效 | `com.hrplatform.workflow.EmployeeChangeService` | `.../workflow/EmployeeChangeService.java` |
| 待办聚合 | `com.hrplatform.workflow.TodoService` | `.../workflow/TodoService.java` |
| 档案借用 | `com.hrplatform.access.ArchiveAccessService` | `.../access/ArchiveAccessService.java` |
| 盘点 | `com.hrplatform.inventory.InventoryService` | `.../inventory/InventoryService.java` |
| 合同 | `com.hrplatform.contract.ContractService` | `.../contract/ContractService.java` |
| 统计 | `com.hrplatform.statistics.StatisticsService` | `.../statistics/StatisticsService.java` |
| 操作日志 | `com.hrplatform.audit.OperationLogService` | `.../audit/OperationLogService.java` |

---

## 2. 状态机类/枚举全限定名

| 状态机 | 类名 | 路径 |
|---|---|---|
| 员工状态枚举 | `com.hrplatform.employee.EmployeeStatus` | `.../employee/EmployeeStatus.java`（`PENDING_ONBOARD/ACTIVE/ON_LEAVE/INACTIVE`） |
| 档案借用状态机 | `com.hrplatform.access.ArchiveAccessStateMachine` | `.../access/ArchiveAccessStateMachine.java` |
| 人事申请状态机 | `com.hrplatform.workflow.RequestStateMachine` | `.../workflow/RequestStateMachine.java` |
| OCR 任务状态 | Pydantic Literal 在 `ocr-service/app/models.py::OcrTask.status` | `ocr-service/app/models.py` |
| 盘点差异/闭环状态 | 字符串字面量在 `com.hrplatform.inventory.InventoryService` | `.../inventory/InventoryService.java` |
| 合同续签状态 | DDL CHECK 在 `V7__contract_schema.sql` | `server/src/main/resources/db/migration/V7__contract_schema.sql` |

---

## 3. 安全与 Web 基础设施

| 用途 | 类名 | 路径 |
|---|---|---|
| 主启动类 | `com.hrplatform.PlatformApplication` | `.../PlatformApplication.java`（`@SpringBootApplication @EnableScheduling @MapperScan`） |
| Spring Security 配置 | `com.hrplatform.common.security.SecurityConfiguration` | `.../common/security/SecurityConfiguration.java` |
| JWT 过滤器 | `com.hrplatform.common.security.JwtAuthenticationFilter` | `.../common/security/JwtAuthenticationFilter.java` |
| JWT 签发/解析 | `com.hrplatform.common.security.JwtService` | `.../common/security/JwtService.java` |
| 当前登录主体 | `com.hrplatform.common.security.JwtPrincipal` | `.../common/security/JwtPrincipal.java` |
| 数据范围 | `com.hrplatform.common.security.DataScope` | `.../common/security/DataScope.java`（`NONE/EMPLOYEE/DEPARTMENT/ALL`） |
| 全局异常处理 | `com.hrplatform.common.web.GlobalExceptionHandler` | `.../common/web/GlobalExceptionHandler.java` |
| 响应包装 | `com.hrplatform.common.web.ApiResponse` | `.../common/web/ApiResponse.java` |
| TraceId 过滤器 | `com.hrplatform.common.web.TraceIdFilter` | `.../common/web/TraceIdFilter.java`（头名 `X-Trace-Id`） |
| 生产配置校验 | `com.hrplatform.common.config.ProductionConfigurationValidator` | `.../common/config/ProductionConfigurationValidator.java` |
| 生产管理员引导 | `com.hrplatform.common.config.ProductionAdminBootstrap` | `.../common/config/ProductionAdminBootstrap.java` |

### 3.1 业务异常 → HTTP 错误码映射（`GlobalExceptionHandler`）
| 异常类 | HTTP | 错误码 |
|---|---|---|
| `MethodArgumentNotValidException` / `HttpMessageNotReadableException` / `IllegalArgumentException` / `InvalidArchiveFileException` | 400 | `VALIDATION_ERROR` |
| `InvalidCredentialsException` | 401 | `UNAUTHORIZED` |
| `AccessDeniedException` / `DataScopeDeniedException` / `ArchiveReadDeniedException` | 403 | `FORBIDDEN` |
| `OcrBindingNotFoundException` / `OcrFieldNotFoundException` / `OcrPreviewMissingException` | 404 | `NOT_FOUND` |
| `RequestStateException` / `ArchiveAccessStateException` / `InventoryStateException` / `OrganizationStateException` / `AccountAdminStateException` | 409 | `STATE_CONFLICT` |
| 其他 `Exception` | 500 | `INTERNAL_ERROR` |

---

## 4. 定时任务

| 类 | 路径 | 触发 |
|---|---|---|
| `com.hrplatform.access.ArchiveAccessExpiryScheduler` | `.../access/ArchiveAccessExpiryScheduler.java` | 60s fixedDelay，把超时 IN_USE/APPROVED 置 ABNORMAL |
| `com.hrplatform.workflow.EmployeeChangeScheduler` | `.../workflow/EmployeeChangeScheduler.java` | 每分钟 cron `0 * * * * *` Asia/Shanghai，扫描到期 APPROVED 申请并生效员工异动 |

---

## 5. OCR 服务关键路径

| 用途 | 路径 |
|---|---|
| FastAPI 应用入口 | `ocr-service/app/main.py::create_app()` |
| 任务编排服务 | `ocr-service/app/service.py::OcrService` |
| PaddleOCR 引擎封装 | `ocr-service/app/engines.py::PaddleOcrEngine` / `MockOcrEngine` |
| 员工档案字段抽取 | `ocr-service/app/extraction.py::EmployeeProfileFieldExtractor` |
| 评测 | `ocr-service/app/evaluation.py::evaluate_samples` |
| MinIO 对象存储 | `ocr-service/app/object_storage.py::MinioObjectStorage` |
| SQLite 任务持久化 | `ocr-service/app/store.py::TaskStore` |
| Pydantic 模型 | `ocr-service/app/models.py` |
| 配置 | `ocr-service/app/config.py::Settings` |
| 本地 SQLite 文件 | `ocr-service/data/ocr_tasks.sqlite3`（运行时生成） |

---

## 6. 前端关键路径

| 用途 | 路径 |
|---|---|
| 公共入口（登录/注册/选工作台） | `web/src/main.ts` + `web/src/router/index.ts` |
| 四端门户入口工厂 | `web/src/portal/create-portal-router.ts` |
| 四端门户元数据/导航 | `web/src/portal/portal-config.ts` |
| 各端 entry ts | `web/src/portal/{employee,manager,hr,admin}-entry.ts` |
| HTTP 客户端 + 类型定义 | `web/src/api/http.ts` |
| Pinia auth store | `web/src/stores/auth.ts` |
| 视图目录 | `web/src/views/`（21 个 .vue 文件，见 04） |
| Vite 配置 | `web/vite.config.ts` |

---

## 7. 数据库与迁移

| 用途 | 路径 |
|---|---|
| Flyway 迁移目录 | `server/src/main/resources/db/migration/V1__*.sql` ~ `V18__*.sql` |
| MyBatis XML | `server/src/main/resources/mapper/**/*.xml` |
| Spring 配置 | `server/src/main/resources/application.yml` / `application-production.yml` |
| 建库 SQL | `infra/mysql/`（首次初始化，按需查阅） |

---

## 8. 部署与脚本

| 用途 | 路径 |
|---|---|
| 中间件 compose | `infra/docker-compose.yml`（mysql/redis/minio） |
| 生产 compose | `infra/docker-compose.production.yml` |
| MinIO 初始化 | `infra/minio-bootstrap.sh` + `infra/minio-policy.json` |
| 演示部署文档 | `docs/demo-deployment-guide.md` |
| 就绪评估（已有） | `docs/project-readiness-assessment.md` |

---

## 9. 已有的设计/分析文档（写作时不要重复造）

| 文档 | 路径 | 内容 |
|---|---|---|
| 就绪评估 | `docs/project-readiness-assessment.md` | P0/P1/P2 问题清单、课题逐项矩阵 |
| 用例分析 | `docs/use-case-analysis.md` | 用例图与描述 |
| 业务流程 | `docs/business-process-design.md` | 业务流程设计 |
| 当前 API 参考 | `docs/current-api-reference.md` | API 列表（无 DTO 字段） |
| 当前数据模型 | `docs/current-data-model.md` | ER 描述（无字段细节） |
| OCR 清晰度评测 | `docs/ocr-clarity-evaluation.md` | 准确率统计口径与结果 |
| 演示部署指南 | `docs/demo-deployment-guide.md` | 部署步骤 |
| drawio 图 | `docs/diagrams/*.drawio` | core-er / use-case / system-architecture / functional-module / business-process |
| 历史 spec/plan | `docs/superpowers/specs/`、`plans/`、`sdd/` | 9 份 spec、18 份 plan |
| 学校模板 | `docs/2027届毕业设计/`（任务书.doc、论文模板.doc、毕设通知.docx） | 论文章节对齐依据 |
