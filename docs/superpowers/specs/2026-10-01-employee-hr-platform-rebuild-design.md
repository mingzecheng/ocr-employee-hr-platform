# 企业员工档案与人事流程管理平台重构设计

**日期：** 2026-10-01  
**状态：** 已确认设计，准备编写实施计划  
**范围：** 保留现有 `ocr-service`，重构其余业务平台

## 1. 背景与调研结论

当前工作区已有一套基于 Spring Boot、Vue 和独立 PaddleOCR 服务的原型实现，但业务代码、服务边界和前端页面需要重新组织。新的实现以课题描述为唯一业务基线，旧 `backend/`、`frontend/` 和业务文档只作为只读参考。

GitHub 未发现与本课题完全相同的开源项目，检索到的相邻项目包括：

- [zongjixiaoai66/HumanResourceManagement](https://github.com/zongjixiaoai66/HumanResourceManagement)：Vue + Spring Boot 的基础人力资源管理系统。
- [wang-buer/Enterprise-Human-Resource-Management-System-Based-on-VUE-and-Springboot-6](https://github.com/wang-buer/Enterprise-Human-Resource-Management-System-Based-on-VUE-and-Springboot-6)：多角色、请假审批、部门、员工和文件管理。
- [zmypor/SpringBoot-Vue-Human-Resources-Management-System](https://github.com/zmypor/SpringBoot-Vue-Human-Resources-Management-System)：用户、岗位、证书和假期等常规 HR 模块。
- [BaeYoungHwan/STrafficFinalProject](https://github.com/BaeYoungHwan/STrafficFinalProject)：PaddleOCR + Spring Boot + Vue，但业务场景是交通项目。

本课题的差异化重点是员工档案资源闭环、固定两级人事审批、原图和 OCR 证据追溯、字段人工修订留痕，以及访问/借用/归还/盘点异常的完整链路。

## 2. 目标与范围

### 2.1 首期 P0 目标

面向员工/业务经办人员、部门负责人、人事管理员和系统管理员，完成以下闭环：

1. 登录、JWT 认证、角色权限和数据范围控制。
2. 部门、岗位、员工主数据和员工状态历史。
3. 员工档案、材料目录、文件对象和不可覆盖的版本历史。
4. 员工档案、入职、调动、离职交接材料的 OCR 识别、字段映射、校验、低置信度提示和人工修订。
5. 入职、调动、离职三类人事申请，固定为部门负责人审批后人事复核。
6. 档案访问/借用申请、授权、使用、归还/移交、超期和异常处理。
7. 盘点任务、差异项、异常处理、统计看板和全程操作日志。
8. 分页检索、组合筛选、受控文件预览/下载和端到端演示脚本。

### 2.2 明确不在首期范围

- 合同管理、批量导入导出、消息推送作为 P1 设计预留，不阻塞 P0 验收。
- 不训练通用 OCR 模型，不修改现有 OCR 引擎和其 SQLite/MinIO 证据实现。
- 不接入真实企业 HR、薪资、社保、电子签名或单点登录系统。
- 不实现可配置的任意多级流程编排，审批节点固定为部门负责人和人事复核。

## 3. 系统架构

采用模块化单体架构。业务平台使用一个 Spring Boot 进程和一个 MySQL 业务库，按领域分包；OCR 服务继续独立运行。

```text
employee-hr-platform/
├── server/                 # 全新的 Spring Boot 模块化单体
├── web/                    # 全新的 Vue 3 + TypeScript 管理端
├── ocr-service/            # 保留现有 FastAPI + PaddleOCR 服务
├── infra/                  # MySQL、Redis、MinIO 本地编排
├── docs/                   # 需求、设计、计划和验收记录
└── legacy-reference/       # 旧业务代码和文档，只读参考，不参与构建
```

### 3.1 服务端模块

`server/src/main/java/com/hrplatform/` 下按业务域组织：

- `common`：统一响应、异常、分页、traceId、文件安全、审计基础设施。
- `auth`：登录、密码校验、JWT、角色、权限和数据范围。
- `organization`：部门树和岗位。
- `employee`：员工主数据和状态历史。
- `archive`：档案、材料、文件对象、版本、受控下载。
- `ocr`：OCR HTTP 客户端、业务字段模板、校验和修订。
- `workflow`：人事申请、流程明细、审批状态机。
- `access`：档案访问/借用、使用、归还和移交。
- `inventory`：盘点任务、差异项和异常处理。
- `statistics`：授权范围内的聚合统计。
- `audit`：操作日志查询和写入适配。

模块之间通过应用服务接口交互，禁止跨模块直接访问 Mapper 或数据库表。事务边界放在应用服务层。

### 3.2 技术栈

- Java 17、Spring Boot 3.x、Spring Security 6。
- MyBatis Spring Boot、MySQL 8、Flyway。
- Redis 仅缓存可重建数据、权限摘要和短时幂等状态。
- Vue 3、Vite、TypeScript、Element Plus 或现有项目已验证的同类组件库。
- OCR 使用现有 Python FastAPI + PaddleOCR 服务及其 HTTP 契约。

## 4. 数据模型

### 4.1 认证和组织

- `sys_user`：登录账号、密码哈希、绑定员工、部门、启用状态。
- `sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`：角色和接口权限。
- `org_department`：部门树，包含 `parent_id`、负责人和启用状态。
- `org_position`：岗位，关联部门和启用状态。
- `employee`：员工编号、姓名、最小必要联系方式、部门、岗位、在职状态和入离职日期。
- `employee_status_history`：员工状态变更前后值、来源申请、操作人和时间。

### 4.2 档案与 OCR 证据

- `employee_archive`：员工档案主记录、完整性、保密级别和归档状态。
- `archive_document`：档案材料分类、标题和保管状态。
- `archive_version`：材料版本号、来源、变更说明、当前标记、发布状态和乐观锁版本。
- `file_object`：原图元数据、媒体类型、字节数、SHA-256 和对象存储引用；对象 key 不直接返回给前端。
- `ocr_binding`：版本 ID、OCR `task_id`、状态、引擎版本、耗时、字段摘要和预览摘要。
- `ocr_field_revision`：业务字段编码、原始值、修订值、原因、操作人和时间。

原图、完整 OCR JSON、文本块和检测框 PNG 不进入 Redis；OCR 原始证据继续由 OCR 服务和 MinIO 保存。

### 4.3 人事流程

- `hr_request`：申请编号、类型、员工、申请人、当前状态、提交时间和乐观锁版本。
- `onboarding_detail`：预计入职日期、目标部门、目标岗位和入职说明。
- `transfer_detail`：原部门/岗位、新部门/岗位、生效日期和调动原因。
- `offboarding_detail`：离职日期、交接人、交接状态和离职原因。
- `approval_record`：申请、节点编码、审批人、决定、意见和时间。

申请状态固定为 `DRAFT`、`PENDING_DEPT_APPROVAL`、`PENDING_HR_APPROVAL`、`APPROVED`、`REJECTED`、`CANCELLED`。审批通过与员工/组织状态更新在同一事务中完成。

### 4.4 档案访问和盘点

- `archive_access_application`：申请编号、申请人、用途、使用方式、有效期、状态和审批信息。
- `archive_access_item`：申请关联的档案材料或具体版本，禁止客户端绕过范围校验。
- `archive_use_record`：领取人、经手人、开始/归还时间、移交对象、状态和异常说明。
- `inventory_task`、`inventory_item`：盘点范围、期望版本、实际状态、差异类型和处理结论。
- `exception_record`：缺件、损坏、位置不符、超期等异常的责任人、说明和处理结果。

访问申请状态为 `DRAFT` → `PENDING_DEPT_APPROVAL` → `PENDING_HR_APPROVAL` → `AUTHORIZED` → `IN_USE` → `RETURNED` → `CLOSED`；异常归还进入 `ABNORMAL`，必须先有异常记录才能完成处理。

### 4.5 审计和统计

`operation_log` 记录登录、上传、OCR、字段修订、审批、授权、下载、归还、盘点和权限变更的操作人、动作、对象、前后摘要、结果、traceId 和时间。

统计使用受数据范围过滤的聚合 SQL，不把统计结果作为事实表保存。首期指标包括员工数量、部门分布、档案完整率、流程数量、访问申请数量、OCR 成功/失败/低置信度数量和平均处理耗时。

## 5. OCR 集成契约

新平台通过 `X-OCR-Internal-Token` 调用 OCR 服务的现有接口：

- `POST /api/ocr/tasks`：文件上传或 MinIO 对象引用；员工档案传 `documentType=employee_profile`，流程材料只传通用图像引用。
- `GET /api/ocr/tasks/{taskId}`：读取状态、文本块、字段、坐标、置信度和错误信息。
- `GET /api/ocr/tasks/{taskId}/detection-preview`：代理检测框 PNG；预览缺失不影响文本结果。
- `PUT /api/ocr/tasks/{taskId}/fields/{fieldCode}`：只在需要同步员工档案基础字段修订时调用，业务层仍保存自己的修订历史。
- `POST /api/ocr/evaluations`：论文评测使用高、中、低清晰度脱敏真值即时计算。

OCR 触发按 `archive_version_id` 幂等；已有成功或处理中任务直接返回绑定。OCR 连接失败、超时或识别失败时，档案版本和原图保留，绑定标记为 `FAILED`，接口返回可处理的业务错误。业务层字段映射模板支持员工档案、入职、调动、离职材料，原始文本块只读。

## 6. 安全与数据范围

- 密码使用 BCrypt；JWT 包含用户 ID、角色、员工 ID、部门 ID 和过期时间。
- 员工只能访问本人或已授权材料；部门负责人只能访问所属部门；人事管理员按授权范围访问；系统管理员维护系统配置。
- 数据范围由服务端从 JWT 和数据库关系计算，客户端传入的部门 ID 不参与授权决策。
- 文件上传校验 MIME、扩展名、实际图片格式、大小和 SHA-256；下载先做范围校验再读取对象存储。
- 列表默认脱敏，日志不记录密码、完整身份证号、原图或完整 OCR JSON。
- Redis 不保存文件二进制、原图、OCR 完整结果、MinIO 私有 key 或令牌。

## 7. 前端页面

新 `web/` 包含登录、工作台、组织员工、档案工作台、OCR 复核、人事流程、档案授权、盘点统计和审计日志页面。页面按角色控制菜单，接口仍由后端权限最终校验。

OCR 复核页同时展示原图、检测框、文本块、字段值、置信度、校验状态和修订历史；切换或卸载时释放 Blob URL。所有页面覆盖加载、空数据、401、403、404、OCR 失败、预览缺失和网络错误状态。

## 8. 测试与验收

- 单元测试覆盖状态机、字段模板、权限范围、文件校验、幂等和异常码。
- MyBatis/Flyway 集成测试覆盖关系约束、分页、事务回滚和乐观锁。
- OCR 契约测试使用 mock HTTP 覆盖成功、低置信度、失败、预览缺失和重复任务。
- 前端测试覆盖登录、路由权限、分页列表、上传、OCR 修订、审批和错误状态。
- 端到端脚本覆盖登录 → 建组织/员工 → 上传 → OCR → 字段修订 → 人事审批 → 档案授权 → 使用归还 → 盘点异常 → 统计审计。
- OCR 评测保留 HIGH/MEDIUM/LOW 脱敏样本，输出字段准确率、检出率、失败率和平均耗时。

## 9. 实施阶段

1. 初始化 Git、编写根 README 和忽略规则，将旧业务目录移动到 `legacy-reference/`。
2. 创建 `server/`，完成统一响应、异常、日志、JWT、权限和 Flyway 基线。
3. 完成组织、岗位、员工及分页查询。
4. 完成档案、材料、文件对象、版本、受控下载和审计。
5. 完成 OCR 客户端、三类流程字段模板、复核和评测接入。
6. 完成人事申请、固定两级审批和员工状态联动。
7. 完成档案访问/借用、使用、归还、移交、异常和盘点。
8. 创建 `web/` 页面，接通核心接口和错误状态。
9. 完成统计、端到端验收、脱敏演示数据、运行文档和 GitHub 发布材料。

## 10. 成功标准

- 新业务平台可以单独启动，OCR 服务通过 HTTP 接入，不依赖 OCR SQLite。
- P0 中每个核心对象都有数据库关系、分页接口、权限校验和操作留痕。
- 旧版本、原图、OCR 原始值和人工修订可分别查询。
- 审批、授权、使用、归还和异常流程可从干净数据库重复演示。
- 端到端验收和自动化测试可复现，使用模拟或脱敏数据，不收集业务无关个人信息。
