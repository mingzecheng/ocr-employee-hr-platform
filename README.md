# 基于 OCR 的企业员工档案与人事流程管理平台

本科毕业设计项目，采用 Spring Boot 3 + MyBatis + MySQL + Vue 3 + PaddleOCR。平台面向员工、部门负责人、人事管理员和系统管理员，覆盖员工档案、OCR 证据、人事审批、档案授权、归还盘点、统计和审计。

## 目录

- `server/`：模块化 Spring Boot 业务平台，按认证、组织、员工、档案、OCR、流程、授权、盘点、统计和审计分包。
- `web/`：Vue 3 + TypeScript + Element Plus 管理端。
- `ocr-service/`：保留的 FastAPI + PaddleOCR 独立服务，只通过 HTTP 接入。
- `infra/`：MySQL、Redis、MinIO 本地依赖编排。
- `docs/`：需求、设计、API、数据库、演示手册和验收记录。
- `legacy-reference/`：旧业务实现，只读参考，不参与新构建。

## 启动

```bash
docker compose -f infra/docker-compose.yml up -d
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f server/pom.xml spring-boot:run
cd web && npm ci && npm run dev
```

默认地址：后端 `http://127.0.0.1:8080`，前端 `http://127.0.0.1:5173`，MinIO `http://127.0.0.1:9001`。OCR 服务安装、启动和评测方式见 `ocr-service/README.md`；没有 Paddle 模型时可设置 `OCR_ENGINE=mock`。

演示账号均为脱敏本地账号，密码为 `password`：`demo-admin`、`demo-hr`、`demo-manager`、`demo-employee`。完整演示顺序、重置命令和数据保留规则见 [`docs/demo-runbook.md`](docs/demo-runbook.md)。

## 验证

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f server/pom.xml test
cd web && npm test -- --run && npm run build
cd ../ocr-service && OCR_ENGINE=mock pytest -q
cd .. && bash scripts/verify-platform.sh
```

真实端到端脚本需要先启动 Docker 服务和 OCR 服务。脚本默认只验证健康检查、登录、组织树、员工分页、统计和审计；设置 `RUN_OCR_SMOKE=1 POSITION_ID=<岗位ID>` 可追加上传与 OCR 触发验证。
