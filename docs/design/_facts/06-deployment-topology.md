# 06 · 部署拓扑事实清单

> 来源：`infra/docker-compose.yml`、`server/src/main/resources/application*.yml`、`ocr-service/app/config.py`、`web/vite.config.ts`、`docs/demo-deployment-guide.md`。

---

## 1. Docker Compose 服务（`infra/docker-compose.yml`）

| 服务 | 镜像 | 端口（绑定 127.0.0.1） | 环境变量 | 数据卷 | 健康检查 |
|---|---|---|---|---|---|
| `mysql` | `mysql:8.0.39` | `${MYSQL_PORT:-3306}:3306` | `MYSQL_ROOT_PASSWORD`、`MYSQL_DATABASE=hr_platform`、`MYSQL_USER=${MYSQL_USERNAME:-hr}`、`MYSQL_PASSWORD` | `mysql_data:/var/lib/mysql` | `mysqladmin ping` |
| `redis` | `redis:7.4-alpine` | `${REDIS_PORT:-6379}:6379` | `--appendonly yes` | `redis_data:/data` | `redis-cli ping` |
| `minio` | `quay.io/minio/minio:latest` | `${MINIO_API_PORT:-9000}:9000`（API）、`${MINIO_CONSOLE_PORT:-9001}:9001`（控制台） | `MINIO_ROOT_USER=${MINIO_ROOT_USER:-minioadmin}`、`MINIO_ROOT_PASSWORD` | `minio_data:/data` | `curl /minio/health/live` |

**未在 compose 中**：Spring Boot 后端、FastAPI OCR 服务、前端静态站点——这三者在开发/演示时本地直接运行。
`infra/docker-compose.production.yml` 存在但本次未展开（按需查阅）。
`infra/minio-bootstrap.sh` + `infra/minio-policy.json` 用于初始化 bucket 与访问策略。

---

## 2. Spring Boot 配置（`application.yml` + `application-production.yml`）

### 2.1 公共配置（`application.yml`，dev 默认）
| 配置项 | 值 | 说明 |
|---|---|---|
| `server.port` | `${SERVER_PORT:8080}` | |
| `spring.datasource.url` | `${MYSQL_URL:jdbc:mysql://127.0.0.1:3306/hr_platform?...}` | |
| `spring.datasource.username/password` | `${MYSQL_USERNAME:hr}` / `${MYSQL_PASSWORD:change-me-application-password}` | |
| `spring.flyway.enabled` | `${FLYWAY_ENABLED:true}` | baseline-on-migrate: true |
| `spring.flyway.placeholders.demoAccountsEnabled` | `${PLATFORM_DEMO_ACCOUNTS_ENABLED:true}` | V16 迁移开关 |
| `spring.data.redis.host/port` | `${REDIS_HOST:127.0.0.1}` / `${REDIS_PORT:6379}` | **仅自动装配，业务代码未使用** |
| `spring.servlet.multipart.max-file-size` | 10MB | |
| `mybatis.mapper-locations` | `classpath:/mapper/**/*.xml` | |
| `platform.jwt.secret` | `${JWT_SECRET:local-development-secret-key-change-before-sharing-please}` | ≥32 字节 |
| `platform.jwt.expiration` | `${JWT_EXPIRATION:PT8H}` | |
| `platform.ocr.base-url` | `${OCR_BASE_URL:http://127.0.0.1:8000}` | |
| `platform.ocr.internal-token` | `${OCR_INTERNAL_TOKEN:local-ocr-internal-token}` | |
| `platform.storage.endpoint/access-key/secret-key/bucket` | MinIO 连接 | bucket 默认 `hr-platform` |
| `platform.security.live-jwt-validation` | `${PLATFORM_LIVE_JWT_VALIDATION:true}` | true 时每个请求回查 sys_user |
| `management.endpoints.web.exposure.include` | `health,info` | |

### 2.2 生产配置（`application-production.yml`）
- 所有密钥改为强制环境变量（无默认值）：`MYSQL_URL/MYSQL_USERNAME/MYSQL_PASSWORD/JWT_SECRET/OCR_BASE_URL/OCR_INTERNAL_TOKEN/MINIO_*`。
- `spring.flyway.placeholders.demoAccountsEnabled: false`。
- `platform.demo-accounts-enabled: false`。
- `server.error.include-message/stacktrace: never`。
- `ProductionConfigurationValidator`（`@Profile("production")`）启动时校验所有密钥不得用默认值，否则拒绝启动。
- `ProductionAdminBootstrap`（`@Profile("production")`）在无 SYSTEM_ADMIN 时用 `BOOTSTRAP_ADMIN_USERNAME/PASSWORD` 创建首个管理员。

### 2.3 定时任务配置
| 配置键 | 默认 | 作用 |
|---|---|---|
| `hr.archive-access.expiry.enabled` | true（matchIfMissing） | 档案借用过期调度开关 |
| `hr.archive-access.expiry-delay-ms` | 60000 | 60 秒 fixedDelay |
| `hr.workflow.employee-change-cron` | `0 * * * * *`（每分钟） | 员工异动生效扫描，时区 Asia/Shanghai |

---

## 3. OCR 服务配置

见 `05-ocr-service.md` 第 6 节。启动命令：`uvicorn app.main:app --host ${OCR_HOST:127.0.0.1} --port ${OCR_PORT:8000}`。

---

## 4. 前端配置（`vite.config.ts`）

| 项 | 值 |
|---|---|
| dev server 端口 | 5173（strictPort） |
| preview 端口 | 4173（strictPort） |
| API proxy | `/api` → `${HR_API_PROXY_TARGET:-http://127.0.0.1:8080}` |
| 构建入口 | 5 个 HTML（index/employee/manager/hr/admin） |
| UI 库 | Element Plus（unplugin-vue-components 自动按需引入） |
| 测试 | vitest + jsdom，setup 文件 `web/src/test/setup.ts` |

---

## 5. 运行时拓扑（开发/演示）

```
┌──────────────────────────────────────────────────────────────┐
│ 浏览器                                                        │
│  ├─ http://127.0.0.1:5173/employee.html  (员工 SPA)          │
│  ├─ http://127.0.0.1:5173/manager.html   (部门负责人 SPA)   │
│  ├─ http://127.0.0.1:5173/hr.html        (人事 SPA)         │
│  ├─ http://127.0.0.1:5173/admin.html     (系统管理 SPA)      │
│  └─ http://127.0.0.1:5173/index.html      (登录/工作台入口)  │
│         │ /api/* 被 Vite proxy 转发                            │
└─────────┼────────────────────────────────────────────────────┘
          ▼
┌────────────────────────────┐     ┌──────────────────────────┐
│ Spring Boot 后端            │────▶│ FastAPI OCR 服务         │
│ :8080                      │HTTP │ :8000                   │
│ (platform.*.yml)          │+token│ (ocr-service/)         │
└──────┬──────────┬─────────┘     └──────┬───────────────────┘
       │          │                       │
       ▼          ▼                       ▼
   ┌───────┐  ┌─────────┐            ┌──────────┐
   │MySQL  │  │ MinIO   │◀──────────▶│ MinIO    │ (同 bucket)
   │:3306  │  │:9000   │  共享文件   │ :9000    │
   │hr_platform│:9001console│       └──────────┘
   └───────┘  └─────────┘
       ▲
       │ Spring Boot 自动装配 RedisTemplate
       │ 但业务代码零使用
   ┌───────┐
   │Redis  │
   │:6379  │
   └───────┘
```

---

## 6. 启动顺序（事实）

1. `docker compose up -d mysql redis minio`（基础设施）
2. 启动 Spring Boot：`./mvnw spring-boot:run`（Flyway 自动跑 V1–V18）
3. 启动 OCR 服务：`uvicorn app.main:app --port 8000`（首次启动加载 PaddleOCR 模型）
4. 启动前端：`npm run dev`（Vite :5173）

**演示账号**（V6/V15 seed，仅当 `PLATFORM_DEMO_ACCOUNTS_ENABLED=true`）：
| 用户名 | 密码 | 角色 |
|---|---|---|
| demo-admin | password | SYSTEM_ADMIN |
| demo-hr | password | HR_ADMIN |
| demo-manager | password | DEPT_MANAGER |
| demo-hr-manager | password | DEPT_MANAGER（人事部门负责人） |
| demo-employee | password | EMPLOYEE |

---

## 7. 关键事实备注

1. **Redis 在 compose 里启动，但 Spring Boot 业务代码不使用**：grep `RedisTemplate/@Cacheable` 在 `server/src/main/java` 下零命中。Redis 容器空转。
2. **MinIO 是 Spring Boot 与 OCR 服务之间的唯一二进制共享通道**：Spring Boot 上传档案到 `archive/` 前缀，OCR 服务通过 `sourceObjectKey` 引用，识别后预览图写到 `ocr/preview/<task_id>.png`。
3. **Spring Boot 与 OCR 服务不共享数据库**：OCR 任务元数据在 OCR 服务自己的 SQLite（`ocr-service/data/ocr_tasks.sqlite3`），Spring Boot 只存 `ocr_binding` 摘要。
4. **生产环境无容器化编排**：compose 只起中间件，后端/前端/OCR 服务需独立部署；`ProductionConfigurationValidator` 拒绝弱密钥启动。
5. **CORS**：后端未配置 CORS 过滤器，开发靠 Vite proxy，生产应同源反向代理。
