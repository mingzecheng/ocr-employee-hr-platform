# 基于 OCR 的企业员工档案与人事流程管理平台

本科毕业设计项目，采用 Spring Boot 3 + MyBatis + MySQL + Vue 3 + PaddleOCR。平台面向员工、部门负责人、人事管理员和系统管理员，覆盖员工档案、OCR 证据、人事审批、档案授权、归还盘点、统计和审计。

## 目录

- `server/`：模块化 Spring Boot 业务平台，按认证、组织、员工、档案、OCR、流程、授权、盘点、统计和审计分包。
- `web/`：Vue 3 + TypeScript + Element Plus 管理端。
- `ocr-service/`：保留的 FastAPI + PaddleOCR 独立服务，只通过 HTTP 接入。
- `infra/`：MySQL、Redis、MinIO 本地依赖编排。
- `docs/`：需求与流程分析、当前 API 参考、当前数据模型、本地演示手册、OCR 评测和验收记录。文档入口：[业务流程](docs/business-process-design.md)、[用例分析](docs/use-case-analysis.md)、[API 参考](docs/current-api-reference.md)、[数据模型](docs/current-data-model.md)、[本地演示手册](docs/demo-deployment-guide.md)、[OCR 评测](docs/ocr-clarity-evaluation.md)。
- `legacy-reference/`：旧业务实现，只读参考，不参与新构建。

工作台以钉钉式待办驱动核心业务：`GET /api/todos` 按当前账号角色和数据范围聚合人事审批、档案授权审批、归还到期和 OCR 失败事项；每条待办带有可继续处理的前端路径。工作台首页和“待办中心”都会展示这些事项，审批或档案授权完成后列表会从业务状态实时重算。

## 应用端

前端提供统一登录入口和四个按角色划分的应用端：

| 应用端 | 本地入口 | 主要职责 |
| --- | --- | --- |
| 员工自助端 | `http://127.0.0.1:5173/employee.html#/` | 本人档案、人事申请、档案访问和个人待办 |
| 部门工作端 | `http://127.0.0.1:5173/manager.html#/` | 部门员工、申请审批、档案借用和盘点 |
| 人事运营端 | `http://127.0.0.1:5173/hr.html#/` | 员工档案、OCR 复核、人事审批、合同和全企业盘点 |
| 系统管理端 | `http://127.0.0.1:5173/admin.html#/` | 账号角色、组织配置、聚合统计和审计 |

统一登录入口为 `http://127.0.0.1:5173/`。单角色账号登录后进入其唯一应用端；多角色账号仅能选择和切换已分配角色对应的应用端。系统管理员默认没有员工档案或 HR 业务权限，应用端路由控制不能替代后端授权。

## 启动

```bash
docker compose -f infra/docker-compose.yml up -d
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f server/pom.xml spring-boot:run
cd web && npm ci && npm run dev
```

默认地址：后端 `http://127.0.0.1:8080`，前端 `http://127.0.0.1:5173`，MinIO `http://127.0.0.1:9001`。OCR 服务安装、启动和评测方式见 `ocr-service/README.md`；没有 Paddle 模型时可设置 `OCR_ENGINE=mock`。按顺序启动依赖、OCR、后端和前端的演示步骤见 [本地演示手册](docs/demo-deployment-guide.md)。
Vite 开发服务器和本地预览服务器都会把 `/api` 转发到 `8080`；后端使用其他端口时，启动前端前设置 `HR_API_PROXY_TARGET=http://127.0.0.1:<后端端口>`。生产部署时，默认 API 地址为同源 `/api`，需由 Web 服务器反向代理至后端；也可在构建前设置 `VITE_API_BASE_URL=http://<后端主机>:<端口>/api`。

仅本地演示环境提供脱敏账号，密码为 `password`：`demo-admin`、`demo-hr`、`demo-manager`、`demo-hr-manager`、`demo-employee`。其中 `demo-hr-manager` 用于人事部的部门审批，`demo-hr` 用于人事复核；生产 profile 会停用这些样例账号和样例组织，并拒绝演示账号认证。

系统管理员演示登录：用户名 `demo-admin`，密码 `password`。人事新建员工档案时由系统自动生成员工编号，员工可用该编号登录。员工注册时填写姓名、档案手机号、密码和确认密码；仅唯一匹配、在职且尚未绑定登录账号的员工档案可以注册。注册账号会立即启用并获得员工自助端权限，部门信息来自员工档案，不能自行选择角色或部门。

生产部署必须启用 `SPRING_PROFILES_ACTIVE=production`，并显式提供 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`JWT_SECRET`、`OCR_BASE_URL`、`OCR_INTERNAL_TOKEN`、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` 和 `MINIO_BUCKET`。数据库密码至少 16 个字符，JWT 密钥和 OCR 内部令牌至少 32 个字符，且不得使用仓库中的本地示例值。首次部署还须提供 `BOOTSTRAP_ADMIN_USERNAME` 和至少 16 个字符的 `BOOTSTRAP_ADMIN_PASSWORD`；系统仅在不存在启用的系统管理员时创建该账号，创建后应从部署环境移除这两个引导变量。OCR 服务须同时设置 `OCR_ENV=production`、`OCR_ENGINE=paddle` 和持久化的绝对 `OCR_DATA_DIR`。生产 profile 启动时会 fail closed；生产迁移会停用 `DEMO-*` 样例账号、员工和组织。业务服务应部署在私有网络之后，并由 HTTPS 反向代理对外提供访问。

### 生产部署

生产 Compose 在私有网络中运行 MySQL、Redis、MinIO、OCR 和 Spring Boot；只有 Caddy 托管的 Web 服务发布 HTTP/HTTPS 端口。数据库、对象存储、OCR 元数据和 Paddle 模型缓存使用具名卷。MinIO 启动时会创建 `hr-platform` bucket 和独立业务账号，应用账号仅有该 bucket 的读写权限。

```bash
cp infra/.env.example infra/.env.production
```

编辑 `infra/.env.production`：设置指向本机公网地址的 `SITE_HOST`，并将示例凭据替换为独立随机值。模板中的默认密钥和演示管理员会被生产启动校验拒绝。可用 `openssl rand -hex 32` 生成 JWT、OCR 令牌及数据库/MinIO 凭据；MinIO root 凭据与业务服务凭据必须不同。首次部署设置非演示管理员账号和至少 16 字节的初始密码。Caddy 需要域名 DNS 指向部署主机，且主机的 80、443 端口可从公网访问，才能自动申请 TLS 证书。

```bash
docker compose --env-file infra/.env.production -f infra/docker-compose.production.yml config
docker compose --env-file infra/.env.production -f infra/docker-compose.production.yml up -d --build
docker compose --env-file infra/.env.production -f infra/docker-compose.production.yml ps
```

首次确认管理员可以登录后，从 `infra/.env.production` 删除 `BOOTSTRAP_ADMIN_USERNAME` 和 `BOOTSTRAP_ADMIN_PASSWORD`。后续部署代码更新时执行 `docker compose --env-file infra/.env.production -f infra/docker-compose.production.yml up -d --build`；更新镜像前先执行备份。MinIO 业务用户凭据变更时，需要在 MinIO 中同步更新 `hr-platform-access` 用户后再滚动重启 `server` 和 `ocr`。

### 备份与恢复

备份覆盖 MySQL、MinIO 的 `hr-platform` bucket 和 OCR SQLite 任务库。脚本先停止 Web 与后端，再等待 OCR 服务优雅停止，之后从 OCR 持久卷生成 SQLite 快照并复制对象数据，避免仍在执行的识别请求把任务最终状态写到快照之外。停机时长取决于正在处理的识别任务、对象数量与总容量；快照完成后脚本按固定顺序重启服务并等待健康检查。数据包使用 `age` 公钥加密，私钥应保存在部署主机之外。先在安全位置生成并保管私钥：

```bash
brew install age
age-keygen -o /secure/path/hr-platform-backup-identity.txt
age-keygen -y /secure/path/hr-platform-backup-identity.txt
```

将输出的 `age1...` 公钥传给备份命令。加密备份和校验文件保存在被 Git 忽略的 `backups/` 目录：

```bash
BACKUP_AGE_RECIPIENT=age1... scripts/backup-platform.sh
```

备份包保存在当前主机的 `backups/` 目录。应将 `.tar.gz.age` 文件及同名 `.sha256` 文件另行复制到独立存储位置，避免主机故障同时损坏业务数据和备份。

恢复会覆盖当前数据库与 bucket 内容，只应在确认目标实例后执行。脚本会先校验加密包、SHA-256 清单和 OCR SQLite 完整性，再停止业务服务并恢复数据；恢复后会等待 OCR 与后端健康检查通过，再启动 Web。若恢复中途失败，业务服务会保持停止，便于检查一致性后再启动：

```bash
scripts/restore-platform.sh \
  --backup backups/hr-platform_<UTC时间>.tar.gz.age \
  --identity /secure/path/hr-platform-backup-identity.txt \
  --confirm
```

## 验证

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f server/pom.xml test
cd web && npm test -- --run && npm run build
cd ../ocr-service && ~/Library/Caches/hr-platform/ocr-service-venv/bin/pytest -q
cd .. && bash scripts/verify-platform.sh
OCR_INTERNAL_TOKEN=local-ocr-internal-token python scripts/evaluate-ocr-clarity.py --base-url http://127.0.0.1:8000
```

真实端到端脚本需要先启动数据库、对象存储、后端、前端和 OCR 服务。脚本默认验证健康检查、登录、组织树、员工分页、统计和审计；设置 `RUN_ADMIN_SMOKE=1` 可追加组织/岗位创建、账号列表、HR/普通账号 403 和系统管理员创建；设置 `RUN_OCR_SMOKE=1 POSITION_ID=<岗位ID>` 可追加档案上传、OCR、档案访问/移交和盘点闭环，设置 `RUN_HR_WORKFLOW_SMOKE=1` 可验证入职、调动、请假、离职、合同及典型审批冲突。管理和人事流程 smoke 使用脱敏 demo 账号并写入 `DEMO-` 前缀数据。
