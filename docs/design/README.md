# 毕业设计设计文档链

> 课题：基于 OCR 的企业员工档案与人事流程管理平台设计与实现
> 作者：樊建威（吉首大学计算机科学与工程学院 · 软件工程 2023 级）
> 文档基线日期：2026-10-13
> 代码基线：Spring Boot 3 + MyBatis + MySQL(Flyway V1–V18) + Vue3/TypeScript/Element Plus(Vite) + FastAPI/PaddleOCR + MinIO

## 阅读顺序

本文档链按"完成情况自审 → 需求 → 架构 → 概要 → 数据库 → 界面 → 详细设计"展开，对应学校论文章节如下：

| 序号 | 文件 | 行数 | 论文章节映射 | 核心内容 |
| --- | --- | --- | --- | --- |
| 00 | [00-completion-review.md](./00-completion-review.md) | 419 | 第1章前言 + 论文自评 | 用 Matt Pocock 方法论（domain-modeling / codebase-design / facts-vs-decisions）重述项目完成度：课题打分矩阵、领域术语表、模块深度评判、7 个状态机清单、P0/P1/P2 缺口 |
| 01 | [01-requirements-analysis.md](./01-requirements-analysis.md) | 541 | 第3章 需求建模 | 业务闭环、四类角色权益、10 大功能域 ~40 条 FR、OCR 专项流水线、非功能需求、2 个核心用例、7 个状态机概览 |
| 02 | [02-architecture.md](./02-architecture.md) | 424 | 第4章 架构设计 | 模块化单体 + 独立 OCR 服务、技术选型、模块依赖、部署拓扑、5 个外部契约、7 条 ADR、现状 vs 目标偏差表 |
| 03 | [03-outline-design.md](./03-outline-design.md) | 411 | 第4章 功能模块设计 | 功能树、模块职责表、模块依赖图、4 个核心流程时序（人事审批/OCR/档案授权/盘点）、横切关注点 |
| 04 | [04-database-design.md](./04-database-design.md) | 671 | 第4章 数据库设计 | 31 张表字段级设计、Mermaid ER、状态字段枚举、版本/审计/留痕机制、一致性约束与目标设计 |
| 05 | [05-ui-design.md](./05-ui-design.md) | 522 | 第5章 界面设计 | 四端门户（员工/部门/HR/管理员）路由与导航、6 个核心页面 ASCII 布局、4 条关键交互流程、统一交互约定 |
| 06 | [06-detailed-design.md](./06-detailed-design.md) | 763 | 第5章 模块设计（类/接口/算法） | 分层架构、6 个核心模块类图、关键接口清单、7 张状态机转移表、OCR 流水线详解、权限模型、异常与日志 |

**合计 3751 行**，外加 [`_facts/`](./_facts/README.md) 目录下 8 份事实清单（1711 行），所有"现状"结论均可追溯到代码 / Flyway 迁移 / 事实清单。

## 配套已有文档（不要重复造）

- [`../project-readiness-assessment.md`](../project-readiness-assessment.md) —— 工程就绪评估（P0/P1/P2 原始清单）
- [`../current-data-model.md`](../current-data-model.md) —— ER 概述（04 文档在其基础上补全字段级）
- [`../current-api-reference.md`](../current-api-reference.md) —— API 表（06 文档在其基础上补状态机与类图）
- [`../use-case-analysis.md`](../use-case-analysis.md)、[`../business-process-design.md`](../business-process-design.md)
- [`../ocr-clarity-evaluation.md`](../ocr-clarity-evaluation.md) —— OCR 清晰度评测原始结果
- [`../diagrams/`](../diagrams/) —— drawio 图源（core-er / use-case / architecture / functional-module / business-process）

## 术语约定（GLOSSARY 种子）

| 术语 | 定义 | 代码锚点 |
| --- | --- | --- |
| 员工档案 | 三层结构：员工主档 → 文档分类 → 版本 | `employee_archive` / `archive_document` / `archive_version` |
| 档案访问（借用） | 员工对档案版本的申请-授权-使用-归还闭环 | `archive_access_application` |
| 人事申请 | 入职/调动/请假/离职四类统一申请 | `hr_request`（`request_type` = ONBOARDING / TRANSFER / LEAVE / OFFBOARDING） |
| 审批记录 | 人事申请的审批时间线 | `approval_record` |
| 访问决定 | 档案访问申请的批准/驳回决定（与"审批记录"不同） | `archive_access_decision` |
| OCR 绑定 | 一个档案版本 ↔ 一个 OCR 任务 | `ocr_binding` |
| 盘点 | 盘点任务 + 盘点项 + 异常记录 | `inventory_task` / `inventory_item` / `exception_record` |
| 操作日志 | 操作人/动作/对象/前后摘要/traceId | `operation_log` |

状态值一律使用代码里的英文枚举，首次出现给中文，如 `PENDING_APPROVAL（待审批）`。

## 现状 vs 目标标注约定

每份文档中：
- **【现状】** = 当前代码已实现，可在 `_facts/` 或 `server/` `web/` `ocr-service/` 中找到出处
- **【目标】** = 课题任务书要求、当前代码尚未闭环，作为论文"展望/改进"章节的素材

已知的 7 处 P0 级"现状 vs 目标"偏差，在 00–06 中均诚实标注：

1. 请假审批未形成"批准→生效→销假"闭环（`EmployeeStatus.ON_LEAVE` 枚举存在但无写入路径）
2. OCR 修订结果没有受控的"确认回填正式档案"接口
3. Redis 容器在跑但业务代码零使用（无 `RedisTemplate` / `@Cacheable`）
4. 单库无读写分离（旧微服务/主从蓝图不是现状）
5. `sys_permission` / `sys_role_permission` 表建了但代码不读，RBAC 只到角色维度
6. 操作日志靠 13 处 Service 手动调 `OperationLogService.record()`，无 AOP 切面
7. OCR 路由是 `async def` 但函数体内无 await，PaddleOCR 推理同步阻塞事件循环，无超时/重试/并发上限

## 测试基线（引用自 readiness-assessment）

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 后端 | `cd server && mvn -q test`（Java 17） | 215 通过，5 项因未配 MySQL 环境变量跳过 |
| 前端 | `cd web && npm test -- --run` | 14 文件 / 38 用例通过 |
| OCR | `ocr-service` pytest | 56 通过 |
| 数据库 | `FlywayMySqlMigrationTest` | V1–V18 迁移、重复 migrate、乐观并发覆盖 |

## Demo 账号

| 账号 | 角色 | 密码 |
| --- | --- | --- |
| `demo-employee` | 企业员工 | `password` |
| `demo-manager` | 部门负责人 | `password` |
| `demo-hr` | 人事管理员 | `password` |
| `demo-hr-manager` | 部门负责人 + 人事（复合） | `password` |
| `demo-admin` | 系统管理员 | `password` |
