# 事实清单索引（_facts/）

> 本目录是"代码事实采集员"的产出，供后续 7 份设计文档（`../00-completion-review.md` ~ `../06-detailed-design.md`）引用。
> 原则：**只列代码/SQL 里真实存在的事实，不做评价、不写设计目标**。所有"已实现"结论都能在源码中找到出处；找不到的明确标"未找到/未实现"。

## 文件清单

| # | 文件 | 内容 |
|---|---|---|
| 01 | [01-backend-modules.md](./01-backend-modules.md) | 后端 13 个包的 Controller 路由表、Service 公开方法签名、Mapper 接口、Security/Web/Config 基础设施、定时任务 |
| 02 | [02-database-tables.md](./02-database-tables.md) | V1–V18 Flyway 迁移反向整理，31 张表逐字段（类型/NULL/默认/索引/外键/CHECK）+ 全表速查表 |
| 03 | [03-state-machines.md](./03-state-machines.md) | 7 个状态机（员工/人事申请/档案借用/OCR/盘点/合同/档案版本）的状态值、转移表、触发位置、代码锚点 |
| 04 | [04-frontend-views.md](./04-frontend-views.md) | 四端门户（employee/manager/hr/admin）路由表、21 个 .vue 页面功能与 API 调用、导航结构 |
| 05 | [05-ocr-service.md](./05-ocr-service.md) | FastAPI 路由、OCR 任务状态机、PaddleOCR 调用链路、字段映射规则、评测口径、与 Spring Boot 的 HTTP 契约 |
| 06 | [06-deployment-topology.md](./06-deployment-topology.md) | docker-compose 服务清单、Spring/OCR/Vite 配置项、运行时拓扑图、启动顺序、演示账号 |
| 07 | [07-key-classes-and-paths.md](./07-key-classes-and-paths.md) | 后续设计文档反复引用的代码锚点（全限定名 + 文件路径）+ 已有文档索引 |

## 与已有 `docs/project-readiness-assessment.md` 的关系

| 主题 | readiness-assessment 已写过 | 本目录新挖的事实 |
|---|---|---|
| P0/P1/P2 问题清单 | ✅ 完整 | 本目录不重复，只在 03/05/06 里给出"代码级证据位置"（如 `EmployeeStatus.ON_LEAVE` 无写入路径、`create_ocr_task` 同步阻塞、Redis grep 零命中） |
| 技术栈与模块划分 | ✅ 概述 | 01 给出每个 Controller 的路由+权限注解、每个 Service 的方法签名 |
| 数据模型 ER | ✅ 高层 ER（`current-data-model.md`） | 02 给出逐字段类型/约束/索引，补全 31 张表 |
| API 列表 | ✅ 路由清单（`current-api-reference.md`） | 01 补全权限角色、请求 DTO record 名；04 补全前端每个页面调用了哪些 API |
| OCR 清晰度评测 | ✅ 结果（`ocr-clarity-evaluation.md`） | 05 给出评测口径、字段映射正则、置信度阈值、同步执行位置 |
| 测试数据 | ✅ mvn 215 / vitest 38 / pytest 56 | 本目录不重复 |

## 本次新发现的、readiness-assessment 未明确的事实

1. **操作日志无 AOP 切面**：`OperationLogService.record()` 由 13 个 Service 类手动调用，grep `@Aspect/@Around` 在 main/java 下零命中。
2. **`sys_permission` / `sys_role_permission` 表未被代码读取**：权限判断完全靠 `@PreAuthorize` 字符串 + `DataScope`，RBAC 只有"角色"维度落地。
3. **OCR 字段不回填正式档案**：grep 未发现把 OCR 字段值写回 `employee`/`employee_archive` 的 Service 方法。
4. **请假状态不闭环**：`EmployeeStatus.ON_LEAVE` 枚举存在但 grep 不到任何写入路径；请假审批通过只写 `employee_change_applied_at`，不改员工状态。
5. **OCR 服务侧 SQLite 与 MySQL 不共享事务**：OCR 任务的完整 text_blocks/fields 在 SQLite，MySQL `ocr_binding` 只存摘要。
6. **生产环境校验在代码里**：`ProductionConfigurationValidator` 启动时拒绝弱密钥、`ProductionAdminBootstrap` 引导首个管理员。
7. **四端是独立 SPA**：通过 5 个 HTML 入口 + 各自 entry ts 实现，共用 views/api。
8. **MinIO 是 Spring Boot 与 OCR 服务之间的唯一二进制通道**：`archive/` 前缀档案被 OCR 服务通过 `sourceObjectKey` 引用，不重复传输。

## 使用约定

- 后续设计文档引用事实时，标注 `（见 _facts/0x-xxx.md）`。
- 状态机/字段/接口以本目录为准；若后续代码变更，同步更新对应文件。
- "现状 vs 目标"在设计文档中分开写：现状来自本目录，目标来自课题任务书。
