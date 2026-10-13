# 05 · OCR 服务事实清单（FastAPI / PaddleOCR）

> 来源：`ocr-service/app/` 下 `main.py / service.py / models.py / extraction.py / engines.py / object_storage.py / store.py / evaluation.py / config.py / visualization.py`。
> 运行方式：独立 Python 服务（uvicorn），与 Spring Boot 通过 HTTP + 共享 MinIO 对象存储解耦；任务元数据落在服务本地 SQLite。

---

## 1. 路由清单（FastAPI）

> 所有 `/api/ocr/*` 路由要求请求头 `X-OCR-Internal-Token: <token>`（中间件 `protect_internal_api` 校验，不匹配返回 403 `FORBIDDEN`）。
> 响应统一包装 `ApiResponse{code, message, data}`（code=0 成功，字符串为错误类型）。

| 方法 | 路径 | 用途 | 关键参数 |
|---|---|---|---|
| GET | `/health` | 健康检查（无需 token） | 返回 `{status:"ok", engine:"paddle"|"mock"}` |
| POST | `/api/ocr/tasks` | 创建 OCR 任务（**同步执行识别**） | multipart：`file`（二选一）或 `sourceObjectKey`+`originalName`+`contentType`+`size`+`sha256`；可选 `documentType`（当前仅支持 `employee_profile`） |
| GET | `/api/ocr/tasks` | 分页查询任务 | `page`、`pageSize`（≤100） |
| GET | `/api/ocr/tasks/{task_id}` | 查询单个任务结果 | path task_id |
| GET | `/api/ocr/tasks/{task_id}/detection-preview` | 返回带检测框的 PNG 效果图 | path task_id |
| GET | `/api/ocr/tasks/{task_id}/field-revisions` | 查字段人工修订历史 | path task_id |
| PUT | `/api/ocr/tasks/{task_id}/fields/{field_code}` | 人工修订某字段 | body `{value, operatorId, reason}` |
| GET | `/api/ocr/statistics` | 任务统计（成功/失败/待复核/修订数/失败原因分布） | — |
| POST | `/api/ocr/evaluations` | 即时评测（按清晰度分组统计准确率） | body `{samples:[{taskId, clarity, expectedFields}]}`，真值不落库 |

---

## 2. 任务内部状态机（SQLite `ocr_tasks.status`）

来源：`models.py` Pydantic Literal
```python
status: Literal["PENDING", "PREPROCESSING", "RECOGNIZING", "SUCCEEDED", "FAILED"]
```

| 状态 | 实际是否写入 | 说明 |
|---|---|---|
| PENDING | 预留 | Literal 声明，`create_task` 直接置 RECOGNIZING |
| PREPROCESSING | 预留 | 预处理（灰度/对比度/缩放）在 `PaddleOcrEngine._preprocess` 内同步完成，不写状态 |
| RECOGNIZING | 是 | `create_task` 入口立即写入 |
| SUCCEEDED | 是 | `engine.recognize()` 成功后 |
| FAILED | 是 | 任何异常被 except 捕获，写 `error_message` |

**持久化**：SQLite 库文件 `<data_dir>/ocr_tasks.sqlite3`，表 `ocr_tasks(task_id PK, payload JSON, updated_at, source_bucket, source_object_key, preview_bucket, preview_object_key, storage_version)`；表 `field_revisions(revision_id PK, task_id, field_code, payload, created_at)`。
任务 payload 是 `OcrTask` 模型的 JSON dump（包含 text_blocks、fields、detection_preview、error_message 等全部证据）。

---

## 3. PaddleOCR 调用链路（预处理 → 识别 → 字段映射 → 预览 → 写库）

来源：`service.py:OcrService.create_task` + `engines.py:PaddleOcrEngine`

```
POST /api/ocr/tasks
  └─ 路由校验：file 与 sourceObjectKey 二选一；MIME ∈ {image/png, image/jpeg}；
     documentType ∈ {"employee_profile"}；文件大小 ≤ 10MB
  └─ 若是 file：Pillow Image.verify() 确认真图片
  └─ OcrService.create_task(...)
       ├─ SourceFileStore.save() 或 register_reference()
       │    └─ 上传/引用到 MinIO，object_key = "ocr/source/<sha256><suffix>"
       ├─ task = OcrTask(status="RECOGNIZING", ...)
       ├─ TaskStore.put(task, references)  ← SQLite 第一次写入
       ├─ with TemporaryDirectory() as directory:
       │    ├─ storage.download_to_path(object_key, source_path)
       │    │    └─ SHA256 一致性校验
       │    ├─ recognition = engine.recognize(source_path)
       │    │    └─ PaddleOcrEngine._preprocess:
       │    │         ImageOps.exif_transpose → grayscale → autocontrast →
       │    │         thumbnail(max_image_edge=4096, LANCZOS) → 存临时 PNG
       │    │    └─ PaddleOcrEngine._predict:
       │    │         优先 ocr.predict()，回退 ocr.ocr(cls=True)
       │    │         (lang="ch", text_det_limit_side_len=4096)
       │    │    └─ PaddleOcrEngine._normalize:
       │    │         统一旧版 dict / 新版 Result / JSON 字符串三种返回，
       │    │         输出 List[TextBlock(pageNo, text, confidence, bbox)]
       │    ├─ task.status = "SUCCEEDED"
       │    ├─ task.text_blocks = recognition.text_blocks
       │    ├─ task.fields = recognition.fields (空)
       │    ├─ if documentType == "employee_profile":
       │    │    task.fields = EmployeeProfileFieldExtractor.extract(text_blocks)
       │    │    （见第 4 节字段映射规则）
       │    └─ task.detection_preview = SourceFileStore.save_detection_preview(...)
       │         └─ visualization.render_detection_preview: 在原图上画检测框、
       │            序号、置信度、识别文本，输出 PNG 上传 MinIO
       │            object_key = "ocr/preview/<task_id>.png"
       ├─ except Exception: task.status="FAILED"; task.error_message=str(exc)
       ├─ finally: task.processing_duration_ms = perf_counter 差值
       └─ TaskStore.put(task, references)  ← SQLite 第二次写入（最终状态）
```

**关键事实：同步执行位置**
- 路由 `create_ocr_task` 是 `async def`，但函数体内**没有 `await` 任何 I/O**，直接 `app.state.ocr_service.create_task(...)`（同步阻塞调用）。
- 整个 PaddleOCR 推理 + MinIO 下载/上传 + Pillow 渲染都在该 HTTP 请求线程内同步完成。
- **无后台队列、无异步任务、无超时控制、无重试**。Spring Boot 侧 `HttpOcrClient` 配置 `readTimeout=30s`，超过即抛 `OcrClientException`，但 OCR 服务仍在继续跑。

---

## 4. 字段映射规则（`EmployeeProfileFieldExtractor`）

来源：`ocr-service/app/extraction.py`

### 4.1 标签 → fieldCode 映射（正则 `^\s*(?P<label>[^:：]+?)\s*[:：]\s*(?P<value>.+?)\s*$`）

| 中文标签 | fieldCode | 校验正则 | 失败提示 |
|---|---|---|---|
| 员工姓名 / 姓名 | `employee_name` | `^[\u4e00-\u9fffA-Za-z· ]+$` | 员工姓名格式错误 |
| 员工编号 / 工号 | `employee_id` | `^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$` | 员工编号格式错误 |
| 身份证号 / 身份证号码 | `id_number` | `^\d{17}[\dXx]$` | 身份证号格式错误 |
| 联系电话 / 手机号码 / 手机号 | `phone` | `^1[3-9]\d{9}$` | 联系电话格式错误 |

### 4.2 复核触发条件
`review_required = (block.confidence < low_confidence_threshold) OR (not validation_passed)`
- 默认阈值 `low_confidence_threshold = 0.85`（环境变量 `OCR_LOW_CONFIDENCE_THRESHOLD`）
- 同一 fieldCode 只取第一个匹配文本块（去重）

### 4.3 字段修订
`PUT /api/ocr/tasks/{task_id}/fields/{field_code}`：
- 重新执行 `validate(field_code, value)`
- `field.validation_status = PASSED/FAILED`
- `field.review_required = not validation_passed`（人工确认后仍不允许以无效格式关闭复核）
- 写一条 `FieldRevision` 到 SQLite `field_revisions` 表

---

## 5. 评测方法（`evaluation.py`）

### 5.1 输入
`OcrEvaluationRequest.samples: list[OcrEvaluationSample]`（≤100 条），每条：
- `taskId`：待评测的 OCR 任务 ID（必须已存在，否则 404 `TASK_NOT_FOUND`）
- `clarity`：`HIGH / MEDIUM / LOW`（清晰度分组）
- `expectedFields`：`{fieldCode: 脱敏真值}`

### 5.2 指标计算口径
| 指标 | 公式 |
|---|---|
| `sampleCount` | 样本数 |
| `expectedFieldCount` | Σ 每条样本的 expectedFields 数 |
| `detectedFieldCount` | 实际识别结果中能找到对应 fieldCode 的数量 |
| `correctFieldCount` | 归一化后与真值字符串相等的字段数 |
| `fieldDetectionRate` | detected / expected |
| `fieldAccuracy` | correct / expected |
| `failedTaskCount` | task.status == FAILED 的样本数 |
| `failureRate` | failedTask / sampleCount |
| `averageProcessingDurationMs` | 所有样本 processingDurationMs 的均值 |

### 5.3 归一化规则
- `employee_id / id_number / phone`：去掉空白与 `-`，转大写
- 其他字段：去掉所有空白，casefold

### 5.4 输出
`OcrEvaluationResult`：整体指标 + `byClarity: [OcrEvaluationGroup]`（仅包含出现过的 HIGH/MEDIUM/LOW 组）。
**真值不落库**，每次请求即时计算。

---

## 6. 配置项（`config.py:Settings`）

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `OCR_ENV` | `development` | =production 时启动校验 |
| `OCR_ENGINE` | `paddle` | `paddle` 或 `mock`（mock 返回固定"员工姓名：张三"等） |
| `OCR_DATA_DIR` | `ocr-service/data/` | SQLite 与旧版文件存放目录 |
| `OCR_MAX_FILE_SIZE_BYTES` | 10485760（10MB） | 上传大小上限 |
| `OCR_MAX_IMAGE_EDGE` | 4096 | 图像最长边限制 |
| `OCR_LOW_CONFIDENCE_THRESHOLD` | 0.85 | 低置信度阈值 |
| `MINIO_ENDPOINT` | `http://127.0.0.1:9000` | MinIO 地址 |
| `MINIO_ACCESS_KEY` | `minioadmin` | |
| `MINIO_SECRET_KEY` | `change-me-minio-password` | |
| `MINIO_BUCKET` | `hr-platform` | |
| `MINIO_ALLOWED_SOURCE_PREFIX` | `archive/` | Spring Boot 侧档案版本 object_key 必须以该前缀开头才允许被 OCR 引用 |
| `OCR_INTERNAL_TOKEN` | `local-ocr-internal-token` | Spring Boot ↔ OCR 互信 token |

**production profile 强制校验**：engine 必须 paddle、data_dir 必须绝对路径、MinIO 凭证不得用默认值、internal_token ≥ 32 字符。

---

## 7. 与 Spring Boot 的契约

来源：`server/src/main/java/com/hrplatform/ocr/HttpOcrClient.java`

| Spring Boot 调用 | OCR 服务路由 | 说明 |
|---|---|---|
| `OcrClient.create(OcrCreateRequest)` | `POST /api/ocr/tasks` | multipart 表单（sourceObjectKey/originalName/contentType/size/sha256/documentType），带 `X-OCR-Internal-Token` 头 |
| `OcrClient.getTask(taskId)` | `GET /api/ocr/tasks/{taskId}` | 查询任务状态与结果 |
| `OcrClient.getPreview(taskId)` | `GET /api/ocr/tasks/{taskId}/detection-preview` | 返回 PNG 字节流；404 + `DETECTION_PREVIEW_NOT_FOUND` 映射为 `OcrPreviewMissingException` |
| `OcrClient.correctField(taskId, fieldCode, value, operatorId, reason)` | `PUT /api/ocr/tasks/{taskId}/fields/{fieldCode}` | JSON body |

**HTTP 客户端配置**：`SimpleClientHttpRequestFactory` connectTimeout=3s、readTimeout=30s；外层包 `BufferingClientHttpRequestFactory`。
**错误映射**：`RestClientResponseException` → `OcrClientException`，消息格式 `"创建 OCR 任务失败 HTTP 500 [错误码] 错误信息"`。

---

## 8. 关键事实备注

1. **同步阻塞**：`create_ocr_task` 路由虽然是 async 函数，但所有 I/O 与推理都是同步调用，FastAPI 事件循环被阻塞。
2. **共享 MinIO，不共享数据库**：Spring Boot 把档案版本文件上传到 MinIO（`archive/` 前缀），OCR 服务通过 `sourceObjectKey` 引用同一份文件，不重复传输二进制；OCR 结果与文本块只存 OCR 服务自己的 SQLite，Spring Boot 通过 `taskId` 回查。
3. **Spring Boot 侧 MySQL `ocr_binding` 只存摘要**：status/engineVersion/processingDurationMs/reviewRequired/errorCode/previewUrl 等元数据；完整 text_blocks/fields 在 SQLite 里，前端要详情时由 Spring Boot 调 `getTask` 透传。
4. **没有"确认回填正式档案"接口**：OCR 字段值目前只在 OCR 服务侧和 `ocr_field_revision` 表留痕，**不会自动/手动写回 `employee` 或 `employee_archive` 表**（grep 未发现相关 service 方法）。这是就绪评估里 P0 缺口的事实依据。
5. **Mock 引擎**：`build_engine("mock")` 返回 `MockOcrEngine`，固定输出两条文本块（"员工姓名：张三"、"员工编号：EMP-001"），用于无 GPU 环境联调与测试。
