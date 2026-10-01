# 后端平台

后端使用 Java 17、Spring Boot 3.4.5、Spring Cloud Gateway、MyBatis、Flyway、MySQL 8 和 Redis 7。

## 本地基础设施

先准备环境变量并启动基础设施：

```bash
cp infra/.env.example infra/.env
# 编辑 infra/.env，至少替换所有 change-me 值
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d
```

Compose 会启动：

- `mysql-primary`：宿主机 `3306`，提供三个业务库的写入端点。
- `mysql-replica`：宿主机 `3307`，通过 GTID 复制提供只读查询端点。
- `redis`：宿主机 `6379`。
- `minio`：S3 API `9000`，控制台 `9001`。

档案原图由 `archive-service` 首次写入 MinIO，OCR 触发时只向 `ocr-service` 发送对象引用；OCR 检测框效果图写入同一 bucket 的 `ocr/preview/{taskId}.png`。两个服务必须使用相同的 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET` 和 `OCR_INTERNAL_TOKEN`。当前本地端口映射为 API `19000`、控制台 `19001` 时，将 `MINIO_ENDPOINT` 配置为 `http://127.0.0.1:19000`。

验证基础设施和后端服务：

```bash
set -a
. infra/.env
set +a
bash scripts/verify-platform.sh
```

验证脚本不会打印任何数据库或对象存储密码。若宿主机的 `3306`、`3307`、`6379` 或 `9000` 已被占用，可在 `infra/.env` 修改对应宿主机端口，并同步调整服务连接环境变量。

## 编译与测试

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml clean test
```

本地按源码启动某个业务服务时，先用 `-am` 构建其公共模块依赖，避免运行进程从本地 Maven 仓库加载旧版公共 JAR：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  mvn -f backend/pom.xml -pl archive-service -am install -DskipTests
```

档案服务还需要把 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` 和 `MINIO_BUCKET` 配置为当前 MinIO 实例的实际凭据；否则图片上传会在对象存储阶段失败。

OCR 对接配置示例：

```bash
MINIO_ENDPOINT=http://127.0.0.1:19000 \
MINIO_ACCESS_KEY=minioadmin \
MINIO_SECRET_KEY='change-me-minio-password' \
MINIO_BUCKET=hr-platform \
OCR_INTERNAL_TOKEN='local-ocr-internal-token' \
mvn -f backend/pom.xml -pl archive-service spring-boot:run
```

启动身份服务：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  MYSQL_WRITE_URL='jdbc:mysql://127.0.0.1:3306/identity_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
  MYSQL_READ_URL='jdbc:mysql://127.0.0.1:3307/identity_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
  MYSQL_USERNAME=hr MYSQL_PASSWORD='your-application-password' \
  mvn -f backend/pom.xml -pl identity-service spring-boot:run
```

启动网关：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml -pl gateway-service spring-boot:run
```

默认端口为网关 `8080`、身份服务 `8081`、档案服务 `8082`、流程服务 `8083`。身份服务的本地开发管理员由 `IDENTITY_ADMIN_PASSWORD` 配置，默认值仅用于本地开发。

## 当前接口

- 网关：`http://127.0.0.1:8080`
- 身份服务健康检查：`GET http://127.0.0.1:8081/actuator/health`
- 登录：`POST http://127.0.0.1:8080/api/auth/login`
- 当前用户：`GET http://127.0.0.1:8080/api/auth/me`
- 部门树：`GET http://127.0.0.1:8080/api/departments/tree`
- 档案服务健康检查：`GET http://127.0.0.1:8082/actuator/health`
- 新建员工：`POST http://127.0.0.1:8080/api/employees`
- 上传档案图片：`POST http://127.0.0.1:8080/api/archive/employees/{employeeId}/documents`
- 触发 OCR：`POST http://127.0.0.1:8080/api/archive/versions/{versionId}/ocr`
- 查询 OCR 结果：`GET http://127.0.0.1:8080/api/archive/versions/{versionId}/ocr-result`
- 检测框效果图：`GET http://127.0.0.1:8080/api/archive/ocr-bindings/{bindingId}/detection-preview`
- 字段复核：`PUT http://127.0.0.1:8080/api/archive/ocr-bindings/{bindingId}/fields/{fieldCode}`

档案 OCR 闭环示例：

```bash
TOKEN="<login response data.accessToken>"
EMPLOYEE_ID=$(curl --fail -sS -X POST http://127.0.0.1:8080/api/employees \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"employeeNo":"E-001","name":"张三"}' | jq -r '.data.id')
VERSION_ID=$(curl --fail -sS -X POST "http://127.0.0.1:8080/api/archive/employees/$EMPLOYEE_ID/documents" \
  -H "Authorization: Bearer $TOKEN" -F documentType=employee_profile -F file=@profile.png \
  | jq -r '.data.versionId')
curl --fail -sS -X POST "http://127.0.0.1:8080/api/archive/versions/$VERSION_ID/ocr" \
  -H "Authorization: Bearer $TOKEN"
```

OCR 识别仍由独立的 `ocr-service` 提供，业务服务通过 HTTP 和 `taskId` 关联 OCR 证据，不直接读取 OCR SQLite 数据库。检测框 PNG 继续由 OCR 服务保存，档案服务只代理效果图和保存其元数据；下游返回 `DETECTION_PREVIEW_NOT_FOUND` 时不会丢弃文字识别结果。

平台验收脚本会执行健康检查、登录、员工列表、图片上传、OCR 识别、重复 OCR 幂等、检测框 PNG 下载和最小审批闭环。默认使用标准 `8080`-`8083` 端口；若本地服务使用其他端口，覆盖对应地址变量：

```bash
GATEWAY_BASE_URL=http://127.0.0.1:18080 \
IDENTITY_BASE_URL=http://127.0.0.1:18081 \
ARCHIVE_BASE_URL=http://127.0.0.1:18082 \
WORKFLOW_BASE_URL=http://127.0.0.1:18083 \
OCR_BASE_URL=http://127.0.0.1:8000 \
FRONTEND_URL=http://127.0.0.1:5173 \
MYSQL_PRIMARY_PORT=13306 MYSQL_REPLICA_PORT=13307 \
MINIO_HEALTH_URL=http://127.0.0.1:19000/minio/health/live \
REDIS_PORT=16379 MYSQL_PASSWORD='your-application-password' \
MYSQL_ROOT_PASSWORD='your-root-password' \
bash scripts/verify-platform.sh
```
