# 基于 OCR 的企业员工档案与人事流程管理平台

本科毕业设计项目，采用 Spring Boot + MyBatis + MySQL + Vue 3 + PaddleOCR。

## 目录

- `server/`：全新的模块化 Spring Boot 业务平台。
- `web/`：Vue 3 + TypeScript 管理端。
- `ocr-service/`：保留的 FastAPI + PaddleOCR 独立服务。
- `infra/`：MySQL、Redis、MinIO 本地依赖编排。
- `docs/`：需求、设计、实施计划和验收记录。
- `legacy-reference/`：原业务实现，只读参考，不参与新构建。

## 本地启动

```bash
docker compose -f infra/docker-compose.yml up -d
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f server/pom.xml spring-boot:run
cd web && npm install && npm run dev
```

OCR 服务的安装、启动和评测方式见 `ocr-service/README.md`。开发阶段可通过 `OCR_ENGINE=mock` 进行业务联调。
