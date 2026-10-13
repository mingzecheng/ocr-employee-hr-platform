# 03 · 状态机事实清单

> 来源：Java 枚举、`*StateMachine.java`、Service 代码里的字符串字面量、Python Pydantic Literal。
> 本文件只列"代码里真实出现的状态值与转移"，不做设计评价。

---

## 1. 员工状态 `employee.status`

**枚举类**：`com.hrplatform.employee.EmployeeStatus`
```java
public enum EmployeeStatus { PENDING_ONBOARD, ACTIVE, ON_LEAVE, INACTIVE }
```

| 状态 | 中文含义 | 进入条件 |
|---|---|---|
| `PENDING_ONBOARD` | 待入职（入职申请已提交未生效） | 入职申请创建时员工主档由 `EmployeeService.create` 初始化；`RequestService.create` 在 ONBOARDING 类型时校验必须为该状态 |
| `ACTIVE` | 在职 | 入职申请审批通过且 `effective_date` 到达后由 `EmployeeChangeService.applyApprovedChange` 写入 |
| `ON_LEAVE` | 请假中 | **代码中未发现自动写入路径**（grep 仅见枚举定义）——请假审批通过后只写 `employee_change_applied_at`，未将 employee.status 切到 ON_LEAVE。**这是就绪评估里"请假无状态生效闭环"的事实依据。** |
| `INACTIVE` | 离职/停用 | 离职申请审批通过且 `leave_date` 到达后写入；V16 在非 demo profile 下批量置 `DEMO-EMP-*` 为 INACTIVE |

**转移规则**（`EmployeeChangeService.applyApprovedChange`，第 95–122 行）：
| 申请类型 | 前置状态校验 | 生效后状态 | 其他副作用 |
|---|---|---|---|
| ONBOARDING | 必须 `PENDING_ONBOARD` | → `ACTIVE` | 写 `hire_date = effectiveDate`；员工关联部门/岗位 |
| TRANSFER | 必须 `ACTIVE` | 保持 `ACTIVE` | 更新 `department_id/position_id`；同步更新 `sys_user.department_id` |
| OFFBOARDING | 必须 `ACTIVE` | → `INACTIVE` | 写 `leave_date`；清空部门/岗位（置 NULL） |

> 生效时机：审批通过后并不立即改员工，而是写 `hr_request.employee_change_applied_at`，由 `EmployeeChangeScheduler`（每分钟 cron）扫描到期记录调用 `applyDueEmployeeChange`。

---

## 2. 人事申请状态 `hr_request.status`

**状态机类**：`com.hrplatform.workflow.RequestStateMachine`
**申请类型**：`ONBOARDING / TRANSFER / LEAVE / OFFBOARDING`（来自 `RequestCreateRequest` 与 V8 CHECK）

### 2.1 状态值
| 状态 | 含义 |
|---|---|
| `DRAFT` | 草稿（已创建未提交） |
| `PENDING_DEPT_APPROVAL` | 待部门负责人审批 |
| `PENDING_HR_APPROVAL` | 待人事审批 |
| `APPROVED` | 审批通过（等待/已生效） |
| `REJECTED` | 审批驳回 |
| `CANCELLED` | 申请人撤回草稿 |

### 2.2 转移表
| 当前状态 | 事件 | 目标状态 | 触发方法 | 角色校验 |
|---|---|---|---|---|
| DRAFT | submit | PENDING_DEPT_APPROVAL | `RequestService.submit` | 申请人本人 |
| DRAFT | cancel | CANCELLED | `RequestService.cancel` | 申请人本人 |
| PENDING_DEPT_APPROVAL | approve(node=DEPARTMENT) | PENDING_HR_APPROVAL | `RequestService.approve` | 必须 `DEPT_MANAGER` |
| PENDING_DEPT_APPROVAL | reject | REJECTED | `RequestService.reject` | DEPT_MANAGER 或 HR_ADMIN |
| PENDING_HR_APPROVAL | approve(node=HR) | APPROVED | `RequestService.approve` | 必须 `HR_ADMIN` |
| PENDING_HR_APPROVAL | reject | REJECTED | `RequestService.reject` | HR_ADMIN |
| APPROVED（OFFBOARDING） | handover | （状态不变，写 `offboarding_detail.handover_status`） | `RequestService.completeHandover` | HR_ADMIN |

> 节点字段 `current_node`：DEPARTMENT 节点审批前为 `DEPARTMENT`，HR 节点审批前为 `HR`，最终 APPROVED 时置 NULL。
> 每个节点写一条 `approval_record`（`uk_approval_request_node(request_id, node_code)` 防重复）。

### 2.3 前置校验（`RequestService.create`）
| 申请类型 | 创建时员工状态要求 |
|---|---|
| ONBOARDING | `PENDING_ONBOARD` |
| TRANSFER | `ACTIVE` |
| LEAVE | `ACTIVE` |
| OFFBOARDING | `ACTIVE`（且 `handover_status` 初始化为 `PENDING`） |

---

## 3. 档案借用申请状态 `archive_access_application.status`

**状态机类**：`com.hrplatform.access.ArchiveAccessStateMachine`

### 3.1 状态值
| 状态 | 含义 |
|---|---|
| `DRAFT` | 草稿 |
| `PENDING_APPROVAL` | 待 HR 审批 |
| `APPROVED` | 已批准（等待开始使用） |
| `REJECTED` | HR 驳回 |
| `IN_USE` | 使用中 |
| `ABNORMAL` | 异常归还（遗失/损坏/未按时还） |
| `RETURNED` | 正常闭环 |

### 3.2 转移表
| 当前状态 | 事件 | 目标状态 | 触发方法 | 角色 |
|---|---|---|---|---|
| DRAFT | submit | PENDING_APPROVAL | `ArchiveAccessService.submit` | EMPLOYEE/DEPT_MANAGER/HR_ADMIN |
| PENDING_APPROVAL | approve | APPROVED | `ArchiveAccessService.approve` | HR_ADMIN |
| PENDING_APPROVAL | reject(reason) | REJECTED | `ArchiveAccessService.reject` | HR_ADMIN（写 `archive_access_decision`） |
| APPROVED | use | IN_USE | `ArchiveAccessService.use` | 申请人 |
| IN_USE | return(abnormal=false) | RETURNED | `ArchiveAccessService.returnAccess` | 申请人（写 `archive_use_record.returned_at`） |
| IN_USE | return(abnormal=true) | ABNORMAL | 同上 | 申请人（记录 `handover_to_employee_id`） |
| ABNORMAL | return(abnormal=true) | ABNORMAL（保持） | 同上（幂等） | 申请人 |
| ABNORMAL | resolveAbnormal(note) | RETURNED | `ArchiveAccessService.resolveAbnormal` | HR_ADMIN（写 `archive_access_resolution`） |

### 3.3 定时任务驱动的转移
| 当前状态 | 触发 | 目标状态 | 调度 |
|---|---|---|---|
| IN_USE 且 `due_at < now` | `ArchiveAccessExpiryScheduler.markOverdueAccessAsAbnormal`（60s fixedDelay） | ABNORMAL | `expireOverdueInUseApplications()` |
| APPROVED 且 `start_at > now`（到点未 use） | 同上调度 | ABNORMAL | `expireUnusedApprovedApplications()` |

---

## 4. OCR 任务状态

### 4.1 OCR 服务侧（SQLite `ocr_tasks.status`，Pydantic Literal）
来源：`ocr-service/app/models.py`
```python
status: Literal["PENDING", "PREPROCESSING", "RECOGNIZING", "SUCCEEDED", "FAILED"]
```

| 状态 | 含义 | 写入位置 |
|---|---|---|
| PENDING | 预留 | Literal 中声明，当前代码 `create_task` 直接置 `RECOGNIZING` |
| PREPROCESSING | 预留 | Literal 中声明，实际预处理在 `PaddleOcrEngine._preprocess` 内同步完成，不写状态 |
| RECOGNIZING | 识别中（任务刚创建） | `OcrService.create_task` 入口 |
| SUCCEEDED | 识别成功 | `create_task` 中 `recognition = engine.recognize(...)` 成功后 |
| FAILED | 识别失败 | `create_task` 中 except 分支，写 `error_message` |

> **关键事实**：`create_task` 是**同步函数**（`async def create_ocr_task` 路由里直接 `app.state.ocr_service.create_task(...)`），整个 PaddleOCR 推理 + MinIO 下载/上传 + SQLite 写入都在 HTTP 请求线程内完成，没有后台队列。

### 4.2 MySQL 侧 `ocr_binding.status`
来源：`OcrService.bindingFromTask`——直接把 OCR 服务返回的 `task.status` 字符串写入 `ocr_binding.status`。
因此取值与上表一致：`PENDING / PREPROCESSING / RECOGNIZING / SUCCEEDED / FAILED`。

### 4.3 派生状态（仅内存 `OcrResult`，不写库）
| 状态 | 含义 | 触发 |
|---|---|---|
| `UNAVAILABLE` | OCR 服务当前不可达 | `OcrService.getResult` 捕获 `OcrClientException` 时派生，`errorCode=OCR_RESULT_UNAVAILABLE` |

### 4.4 OCR 字段级状态
- `validationStatus`：`PASSED / FAILED`（格式校验）
- `reviewRequired`：bool，任一字段置信度 < 0.85 或校验失败则 true
- `errorCode`（binding 级）：成功时 null，失败时 `OCR_FAILED`

---

## 5. 盘点任务状态

### 5.1 `inventory_task.status`
| 状态 | 含义 | 进入位置 |
|---|---|---|
| `DRAFT` | 草稿 | `InventoryService.create`（line 50） |
| `IN_PROGRESS` | 盘点中 | `InventoryService.start`（line 107，`transitionTask(id,"DRAFT","IN_PROGRESS")`） |
| `COMPLETED` | 已完成 | `InventoryService.complete`（line 304，`transitionTask(id,"IN_PROGRESS","COMPLETED")`） |
| `ABNORMAL` | 异常（在 `complete` 校验中出现，line 211） | 当存在 OPEN 差异项时不允许 complete |

### 5.2 `inventory_item.resolution_status`（V12）
| 状态 | 含义 |
|---|---|
| `PENDING` | 新建盘点项初始值 |
| `NOT_REQUIRED` | 盘点无差异（`difference_type=NONE`），无需闭环 |
| `OPEN` | 存在差异，待 HR/负责人闭环 |
| `RESOLVED` | 差异已闭环（`InventoryService.resolve`，line 223） |

### 5.3 `inventory_item.difference_type`
| 值 | 含义 | 写入位置 |
|---|---|---|
| `NONE` | 实际版本 == 期望版本 | line 178 三元表达式 |
| `VERSION_MISMATCH` | 实际版本 != 期望版本 | 同上 |

### 5.4 转移规则（`InventoryService`）
| 当前 task 状态 | 事件 | 目标 | 校验 |
|---|---|---|---|
| DRAFT | start | IN_PROGRESS | 必须 DRAFT |
| IN_PROGRESS | check(item) | （task 不变） | item.resolutionStatus 必须 PENDING 且 actualStatus 为 null |
| IN_PROGRESS | complete | COMPLETED | 所有 item 必须已 check（resolutionStatus != PENDING）；无 OPEN 项 |
| IN_PROGRESS | resolve(item) | （task 不变） | task 必须 IN_PROGRESS 或 ABNORMAL；item 必须 OPEN；写 exception_record.status=RESOLVED |

---

## 6. 合同状态 `employee_contract`

| 字段 | 取值 | 来源 |
|---|---|---|
| `renewal_status` | `NOT_DUE`（默认）/ `RENEWED` / `NOT_RENEWING` | V7 CHECK 约束 |
| `status`（前端派生） | `ACTIVE` / `EXPIRED` | 由 `ends_on` 与当前日期比较得出，**数据库无此字段**；`ContractService.list` 按入参 status 过滤 |
| 前端额外显示 | `DUE`（即将到期） | 前端 Dashboard 按 `renewalStatus=DUE` 查询触发，后端实际查询是按 `renewal_status IN (...)` 派生 |

> **事实备注**：合同到期提醒是"查询时派生"，没有定时任务扫描到期合同；`markNotRenewing` 是唯一的写操作。

---

## 7. 档案版本状态 `archive_version.status`

| 值 | 含义 |
|---|---|
| `UPLOADED` | 默认值（V2 DDL）；代码中未发现其他状态字面量，实际版本一旦上传即"当前版本"（`is_current=true`），新版本上传时旧版本自动 `is_current=false` |

---

## 8. 字段级校验状态（OCR）

| 位置 | 字段 | 取值 |
|---|---|---|
| `ocr_field_revision` | （无状态字段，仅时间序列） | — |
| `BusinessField.validationStatus` | 业务字段 | `PASSED / FAILED` |
| `BusinessField.reviewRequired` | bool | true 表示需要人工复核 |

---

## 9. 状态机汇总图（文字版）

```
employee:        PENDING_ONBOARD --入职生效--> ACTIVE --离职生效--> INACTIVE
                                               |--(LEAVE 未闭环)--> (ON_LEAVE 未实现)

hr_request:      DRAFT --submit--> PENDING_DEPT_APPROVAL --dept approve--> PENDING_HR_APPROVAL --hr approve--> APPROVED
                 DRAFT --cancel--> CANCELLED
                 PENDING_* --reject--> REJECTED

archive_access:  DRAFT --submit--> PENDING_APPROVAL --approve--> APPROVED --use--> IN_USE --return(正常)--> RETURNED
                                                     \--reject--> REJECTED                  \--return(异常)--> ABNORMAL --resolve--> RETURNED
                 IN_USE/APPROVED 超时 --定时任务--> ABNORMAL

ocr_binding:     (trigger) --RECOGNIZING--> SUCCEEDED
                                       \--> FAILED
                 (查询时 OCR 服务不可达) --派生--> UNAVAILABLE（不写库）

inventory_task:  DRAFT --start--> IN_PROGRESS --complete--> COMPLETED
inventory_item:  PENDING --check(NONE)--> NOT_REQUIRED
                 PENDING --check(VERSION_MISMATCH)--> OPEN --resolve--> RESOLVED

contract:        NOT_DUE --markNotRenewing--> NOT_RENEWING  (RENEWED 由新合同 renewed_from_contract_id 派生)
```
