# 前端闭环报告（F-1 / F-2 / F-3）

> 日期：2026-10-13
> 范围：后端 OCR 确认/拒绝端点、ON_LEAVE 状态、审批幂等 409 均已就绪后，前端补齐对应的按钮、状态标签与错误处理。
> 方法：TDD（红→绿），不改动任何后端代码（`server/`、`ocr-service/` 全部不动），不破坏既有前端测试基线。

## 测试基线对比

| 套件 | 改造前 | 改造后 | 新增 | 状态 |
|---|---|---|---|---|
| 前端 `npm test -- --run` | 38 | **50** | +12 | ✅ 14 文件全过 |
| 前端 `npm run build`（vue-tsc --noEmit + vite build） | — | **通过** | — | ✅ 无类型错误 |

新增测试分布：`api/http.test.ts` +1、`OcrReviewView.test.ts` +7、`EmployeeArchiveView.test.ts` +1、`EmployeeListView.test.ts` +1、`TodoDetailView.test.ts` +2。

---

## F-1 OCR 确认 / 拒绝按钮 ✅

### 改动文件
| 文件 | 改动 |
|---|---|
| `web/src/views/OcrReviewView.vue` | 字段面板底部新增确认操作栏：HR 可见「确认回填档案」(primary) 与「标记为无效」(danger plain)；低置信度未修订时禁用确认按钮并红色提示；已 CONFIRMED/REJECTED 时按钮替换为状态标签；确认前 `ElMessageBox.confirm` 列出白名单字段前后值对比；成功后 toast 并本地翻转 confirmStatus；403/400/409 分支化错误处理。 |
| `web/src/views/EmployeeArchiveView.vue` | `triggerOcr` 跳转 `/ocr/:versionId` 时附带 `query.employeeId`，供复核页拉取员工主档做前后值对比。 |
| `web/src/api/http.ts` | `OcrResult` 增加可选 `confirmStatus/confirmNote/confirmedAt`（前向兼容）；新增 `OcrConfirmResult` 类型。 |

### 演示路径（锁定在哪个测试）
- HR 可见两个按钮、字段全部置信度≥0.8 且 PASSED 时确认按钮可用 → `OcrReviewView > offers confirm and reject actions to HR...`
- 白名单字段置信度 0.55 且未人工修订 → 确认按钮 disabled + 红色提示 → `...disables the confirm button while a whitelist field is low-confidence...`
- 该字段有人工修订记录后 → 确认按钮恢复可用 → `...re-enables confirm once the low-confidence field has been human-revised`
- 已 CONFIRMED → 显示绿色「已确认」标签、隐藏按钮 → `...shows a confirmed status tag...`
- 已 REJECTED → 显示灰色「已拒绝」标签 → `...shows a rejected status tag...`
- 点击确认 → 弹出对比框 → 调 `POST /archive/ocr-bindings/{id}/confirm` → 翻转为「已确认」 → `...calls the confirm endpoint after the dialog and flips to the confirmed tag`
- 非 HR 角色不显示按钮 → `...hides confirm/reject actions for non-HR roles`

白名单字段（employee_name / employee_id / phone / id_number）与后端 `OcrConfirmService.CONFIRMABLE_FIELDS` 保持一致；低置信度阈值 0.8 与后端 `LOW_CONFIDENCE` 一致。

---

## F-2 请假状态前端可见 ✅

### 改动文件
| 文件 | 改动 |
|---|---|
| `web/src/views/EmployeeArchiveView.vue` | 页头员工标题旁：`status === 'ON_LEAVE'` 时显示蓝色 `el-tag type="primary"`「请假中」；副标题展示请假起止日期（`leaveStartDate ~ leaveEndDate`，字段缺失时显示 ?）。 |
| `web/src/views/EmployeeListView.vue` | 状态列：ON_LEAVE 标签改蓝色 `type="primary"`，文案由「休假」改为「请假中」。 |
| `web/src/api/http.ts` | `Employee` 增加可选 `leaveStartDate/leaveEndDate`（前向兼容）。 |

### 演示路径（锁定在哪个测试）
- 档案详情页员工 ON_LEAVE → 出现蓝色「请假中」标签并显示起止日期 → `EmployeeArchiveView > shows a blue on-leave badge with date range...`
- 员工列表行 ON_LEAVE → 状态列渲染「请假中」蓝色标签 → `EmployeeListView > renders a blue on-leave badge for employees currently on leave`

---

## F-3 审批幂等 409 友好处理 ✅

### 改动文件
| 文件 | 改动 |
|---|---|
| `web/src/api/http.ts` | 响应拦截器：HTTP 409 时统一 `ElMessage.warning('操作正在处理中，请勿重复提交')`，并在规范化错误上打 `handled = true` 标记，避免视图层重复弹层。 |
| `web/src/views/TodoDetailView.vue` | 新增 `decisionPending`：通过/驳回按钮 `:loading :disabled` 绑定，请求在途时禁用防重复提交；`decide()` catch 中 `handled` 错误跳过通用 error toast。 |

### 演示路径（锁定在哪个测试）
- 拦截器对 409 弹出友好 toast、错误标记 handled → `http > surfaces a friendly conflict toast and flags the error as already handled on 409`
- HR 点击「通过」→ 填意见 → 调 `POST /hr-requests/{id}/approve`，请求在途按钮 disabled → `TodoDetailView > calls the approve endpoint after the comment prompt and disables the button while in flight`
- 重复提交命中 409（handled 错误）→ 按钮恢复可用、页面不崩 → `...restores the buttons after a duplicate-submit 409 conflict without crashing`

---

## 仍存在的限制 / 后续建议

1. **ocr-result 未回传 confirmStatus**：当前 `GET /archive/versions/{versionId}/ocr-result` 返回的 `OcrResult` 不含 `confirmStatus`。前端已做会话内状态翻转（确认/拒绝后本地置位），但**刷新页面后按钮会重新出现**。前端类型 `OcrResult.confirmStatus` 已预留，待后端在 `OcrResult` 记录中补该字段后即可自动持久化终态展示，无需再改前端。
2. **OCR 校验失败的 HTTP 状态**：`OcrConfirmService` 抛出的 `IllegalStateException`（低置信度未修订、已拒绝再确认等）当前落入全局兜底 `Exception` → 500 且 message 被替换为「服务暂时不可用」，前端拿不到具体字段原因。前端已按 400/403/409 分支预留，待后端将该校验异常映射为 400（VALIDATION_ERROR）后，低置信度原因即可透传到 toast。
3. **请假起止日期来源**：`/employees/{id}` 详情当前不返回 `leaveStartDate/leaveEndDate`（`Employee` 记录仅有 hireDate/leaveDate）。前端字段已预留并渲染，待员工详情接口补这两个字段后，日期即自动显示；当前演示中 ON_LEAVE 标签本身立即可见。
4. **未触碰后端**：按约束 `server/`、`ocr-service/` 零改动；以上 1–3 均为后端可独立闭环的前向兼容点，前端无破坏性。
