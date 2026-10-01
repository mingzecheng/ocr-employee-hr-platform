# 脱敏演示手册

## 启动

```bash
docker compose -f infra/docker-compose.yml up -d
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f server/pom.xml spring-boot:run
cd web && npm ci && npm run dev
```

OCR 服务按 `ocr-service/README.md` 启动。没有 Paddle 模型时可以设置 `OCR_ENGINE=mock`，业务平台仍会完整演示 HTTP、字段映射、低置信度和修订流程。

## 账号

| 账号 | 密码 | 角色 | 范围 |
| --- | --- | --- | --- |
| `demo-admin` | `password` | 系统管理员 | 全部 |
| `demo-hr` | `password` | 人事管理员 | 全部 |
| `demo-manager` | `password` | 部门负责人 | 演示运营部 |
| `demo-employee` | `password` | 员工/经办人 | 本人 |

## 演示顺序

1. 以 `demo-admin` 登录，查看工作台、员工分页、组织树和统计指标。
2. 打开员工档案，上传 `ocr-service/tests/fixtures/platform-smoke.png`，检查版本号和 SHA-256。
3. 点击 OCR 识别，查看原始文本块、字段置信度、格式校验和人工修订历史。
4. 以 `demo-manager` 查看待审批调动流程；以 `demo-hr` 完成人事复核。
5. 创建档案访问申请，完成提交、授权、使用和归还；逾期或异常归还会进入异常状态。
6. 创建盘点任务，解决每个差异后再完成任务；未解决差异会返回 `STATE_CONFLICT`。
7. 在审计日志查看上传、OCR、审批和状态变更的动作及 traceId。

## 重置

只删除 `DEMO-` 记录：

```bash
MYSQL_PASSWORD=change-me-application-password bash scripts/reset-demo-data.sh --confirm
```

脚本不会删除 OCR 服务数据、MinIO 非演示对象或任何不带 `DEMO-` 前缀的业务数据。
