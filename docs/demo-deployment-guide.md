# 本地演示与交付运行手册

本手册用于本科毕业设计的本地脱敏演示。生产部署参数、备份和恢复操作见根目录 [README](../README.md)。演示数据不代表真实员工信息。

## 环境

- Java 17 或更高版本、Maven、Node.js（项目 lockfile 对应版本）、Python 3.10 或更高版本。
- Docker Compose 用于 MySQL、Redis 和 MinIO。
- 首次运行 PaddleOCR 时需要下载模型；后续使用本机缓存。

## 启动

在仓库根目录启动本地依赖：

```bash
docker compose -f infra/docker-compose.yml up -d mysql redis minio
```

在独立终端准备并启动 OCR 服务。开发时后端与 OCR 服务的 `OCR_INTERNAL_TOKEN` 必须相同；默认本地令牌仅用于演示环境：

```bash
cd ocr-service
python3.10 scripts/run_local.py --install-only
OCR_INTERNAL_TOKEN=local-ocr-internal-token python3.10 scripts/run_local.py
```

在两个新的终端中，从仓库根目录分别启动平台后端和前端：

```bash
mvn -f server/pom.xml spring-boot:run
```

```bash
cd web
npm ci
npm run dev
```

Flyway 在后端启动时应用数据库迁移。首次启动前确保 MySQL、Redis 和 MinIO 已健康可用。默认服务地址如下：

| 服务 | 地址 |
| --- | --- |
| 登录入口 | `http://127.0.0.1:5173/` |
| 员工自助端 | `http://127.0.0.1:5173/employee.html#/` |
| 部门工作端 | `http://127.0.0.1:5173/manager.html#/` |
| 人事运营端 | `http://127.0.0.1:5173/hr.html#/` |
| 系统管理端 | `http://127.0.0.1:5173/admin.html#/` |
| Spring Boot API | `http://127.0.0.1:8080` |
| OCR 健康检查 | `http://127.0.0.1:8000/health` |
| MinIO 控制台 | `http://127.0.0.1:9001` |

## 演示账号

默认密码为 `password`，仅适用于本地 demo profile。生产 profile 会停用这些账号和样例组织。

| 用户名 | 演示角色 | 应用端 |
| --- | --- | --- |
| `demo-employee` | 员工 | 员工自助端 |
| `demo-manager` | 部门负责人 | 部门工作端 |
| `demo-hr-manager` | 人事部门负责人 | 部门工作端、人事相关审批 |
| `demo-hr` | 人事管理员 | 人事运营端 |
| `demo-admin` | 系统管理员 | 系统管理端 |

多角色账号只能在已分配角色对应的应用端之间切换。系统管理员账号不因此获得员工档案或 HR 业务权限。

## 建议演示顺序

1. 使用 `demo-hr` 创建或检查员工、组织岗位和档案记录。新员工编号由系统生成。
2. 上传脱敏档案文件，触发 OCR，展示原图、检测框、识别字段、校验/低置信度标记及人工修订历史。
3. 使用员工账号提交一种人事申请；由对应部门负责人审批，再由 `demo-hr` 完成人事复核。申请详情应展示明细和审批时间线。
4. 以员工身份申请访问或借用档案，使用 `demo-hr` 审批；员工开始使用后登记归还、移交或异常归还，检查操作历史。
5. 使用部门负责人或人事账号创建盘点，核对档案版本。制造差异后先登记处理说明，再完成任务。
6. 使用 `demo-hr` 登记合同并关联档案来源版本，演示续签或不续签状态和合同列表。
7. 使用 `demo-admin` 查看账号、组织配置、聚合统计和审计记录；验证其不能访问员工档案明细。

## 员工注册

注册页只填写姓名、档案手机号、密码和确认密码。员工编号、角色及部门不由注册者输入：平台按姓名与手机号匹配唯一的在职员工档案，注册成功后立即绑定员工账号并授予员工权限。没有唯一匹配、员工不在职或档案已绑定账号时，注册会被拒绝；应先由 HR 核验员工档案。

## OCR 清晰度评测

服务启动后，在仓库根目录运行 36 张合成图像评测。真值随请求传递，不写入任务库：

```bash
OCR_INTERNAL_TOKEN=local-ocr-internal-token \
python scripts/evaluate-ocr-clarity.py --base-url http://127.0.0.1:8000
```

样例限制和本轮指标见 [OCR 清晰度评测记录](ocr-clarity-evaluation.md)。论文实验应使用获准的脱敏材料，并单独保存样本版本、人工真值、引擎版本和运行环境。

## 常见故障定位

| 现象 | 检查项 |
| --- | --- |
| 后端无法连接数据库 | Compose 中 MySQL 是否健康；`MYSQL_URL`、用户名和库名是否与本地配置一致。 |
| 登录后 API 返回 401 | 前端是否经 Vite `/api` 代理访问 8080；令牌是否过期；登录和当前角色是否有效。 |
| OCR 返回 403 | 后端 `OCR_INTERNAL_TOKEN` 与 OCR 进程环境变量是否完全一致。 |
| OCR 任务失败或结果暂不可用 | 检查 OCR 服务日志、Paddle 模型加载和 MinIO 健康状态；区分识别失败和服务暂不可用。 |
| 档案图片上传或 OCR 对象引用失败 | 检查 MinIO bucket、上传对象键前缀和服务端存储配置。 |
| 页面没有后端响应 | 开发服务器代理目标应指向实际 Spring Boot 地址；后端端口调整时设置 `HR_API_PROXY_TARGET` 后重启 Vite。 |

不要把生产密钥、真实员工信息、身份证件或真实合同放入演示录屏、日志和仓库。生产变量校验与加密备份流程以根目录 README 为准。
