# Local OCR Service

这是员工档案与人事流程平台的独立本地 OCR 服务。用户上传 PNG、JPEG 图片后，服务保存原图，同步使用 PaddleOCR 识别中文和英文文本，并返回可持久化、可查询的 OCR 证据。

默认模式只负责图像文本识别：服务返回原始文本块、置信度和位置坐标。上传时可额外传入 `documentType=employee_profile`，启用员工姓名、员工编号、身份证号和联系电话的基础字段映射、格式校验及低置信度复核标记。后续员工档案或人事流程服务应通过 `taskId` 关联 OCR 结果、原图及自己的字段抽取结果。

## 安装依赖

项目要求 Python 3.10 及以上。使用项目虚拟环境安装依赖：

```bash
.venv/bin/python -m pip install -r requirements.txt
```

已固定 CPU 运行时版本为 `paddlepaddle==3.3.0`，并使用 `paddleocr>=3.0,<4`。首次以 `OCR_ENGINE=paddle` 启动时，PaddleOCR 会下载所需中文模型并缓存到本机；后续启动会复用缓存。

## PyCharm 一键启动

1. 用 PyCharm 打开 `ocr-service` 目录。
2. 在 Python Interpreter 中选择项目内的 `.venv/bin/python`；依赖可从 `requirements.txt` 导入。
3. 在右上角运行配置中选择 `Local OCR Service`。
4. 点击绿色运行按钮，服务会自动执行 `app/main.py` 并监听 `127.0.0.1:8000`。

运行配置已经保存在 `.idea/runConfigurations/Local_OCR_Service.xml`，打开项目后会自动出现在 PyCharm 的运行配置列表中。

启动后可访问：

- OpenAPI 文档：`http://127.0.0.1:8000/docs`
- 健康检查：`GET http://127.0.0.1:8000/health`

## 接口示例

上传图片并直接获取 OCR 结果：

```bash
curl -X POST http://127.0.0.1:8000/api/ocr/tasks \
  -F file=@./sample.png
```

上传员工档案并启用字段映射：

```bash
curl -X POST http://127.0.0.1:8000/api/ocr/tasks \
  -F documentType=employee_profile \
  -F file=@./employee-profile.png
```

根据任务 ID 查询已保存的识别结果：

```bash
curl http://127.0.0.1:8000/api/ocr/tasks/<taskId>
```

获取该任务的文字检测框效果图：

```bash
curl http://127.0.0.1:8000/api/ocr/tasks/<taskId>/detection-preview \
  --output detection-preview.png
```

分页查询任务列表（默认每页 20 条，最大 100 条）：

```bash
curl 'http://127.0.0.1:8000/api/ocr/tasks?page=1&pageSize=20'
```

查询本地 OCR 运行统计：

```bash
curl http://127.0.0.1:8000/api/ocr/statistics
```

使用人工标注的脱敏字段真值，评测高、中、低清晰度样本的字段准确率、检出率、失败率和平均耗时。评测真值仅用于本次请求，不会保存到本地：

```bash
curl -X POST http://127.0.0.1:8000/api/ocr/evaluations \
  -H 'Content-Type: application/json' \
  -d '{
    "samples": [
      {
        "taskId": "<taskId>",
        "clarity": "HIGH",
        "expectedFields": {
          "employee_name": "张三",
          "employee_id": "EMP-001"
        }
      }
    ]
  }'
```

人工修订一个已映射字段，并保留修订前后值：

```bash
curl -X PUT http://127.0.0.1:8000/api/ocr/tasks/<taskId>/fields/employee_name \
  -H 'Content-Type: application/json' \
  -d '{
    "value": "张三",
    "operatorId": "hr-001",
    "reason": "依据原图复核修正"
  }'
```

查询该任务的全部字段修订记录：

```bash
curl http://127.0.0.1:8000/api/ocr/tasks/<taskId>/field-revisions
```

所有业务接口返回 `code`、`message`、`data`。识别成功时任务状态为 `SUCCEEDED`；返回数据包含：

- `taskId`：OCR 任务的唯一标识，业务系统使用它关联档案版本或流程材料。
- `sourceFile`：保存后的原图元数据和 SHA-256。
- `engineVersion`：本次任务使用的 OCR 引擎或模型版本，可作为评测和问题追溯依据。
- `detectionPreview`：保留原图内容并绘制文本检测框、序号、置信度和识别文本的派生 PNG 效果图；原图与效果图分开保存。
- `textBlocks`：识别结果；每项包含 `text`、`confidence`、`pageNo` 与 `bbox`。
- `bbox`：原图像素坐标，格式为 `[left, top, right, bottom]`。即使内部为控制资源占用而缩小图片，接口也会恢复为原图坐标。
- `fields`：默认是 `[]`；指定 `documentType=employee_profile` 时，返回员工姓名、员工编号、身份证号和联系电话等可识别字段。
- `reviewRequired`：字段置信度低于阈值，或格式校验失败时为 `true`，调用方应提示人事人员复核。
- 人工修订后会重新执行字段格式校验；有效修订会清除该字段的低置信度复核提示，无效格式仍会保留 `reviewRequired: true`。
- `field-revisions`：保存修订前后值、业务操作人、修订原因和时间，供档案版本与审批记录关联。

识别成功后可以通过任务返回的 `detectionPreview.url` 获取检测框效果图。效果图只用于可视化和问题排查，不替代原始上传文件；识别失败的任务保留原图和失败信息，但不会生成没有检测结果的效果图。

对于效果图功能上线前已经创建的成功任务，如果原图和 OCR 文本块仍然存在，首次访问上述地址时服务会自动补生成效果图并回写任务记录；因此历史任务不需要重新上传或重新识别。

原图和检测框效果图统一保存在 MinIO：原图使用 `archive/...`（档案 OCR）或 `ocr/source/{sha256}...`（独立上传），效果图使用 `ocr/preview/{taskId}.png`。`OCR_DATA_DIR` 只保存 `ocr_tasks.sqlite3`、任务 JSON、字段修订和 MinIO 对象引用；识别期间下载到系统临时目录的文件会在任务结束后清理。识别失败时任务状态为 `FAILED`，MinIO 中的原图仍保留。

历史版本曾把图片写入 `ocr-service/data` 或 `ocr-service/app/data`。迁移期间可通过 `OCR_LEGACY_DATA_DIR` 只读访问这些目录；迁移完成后使用下方脚本将二进制上传并校验，再删除本地图片。新任务不会再写入任何 `data/*.png` 或 `data/previews/*.png`。

响应示例：

```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "taskId": "<taskId>",
    "status": "SUCCEEDED",
    "sourceFile": {"sha256": "..."},
    "textBlocks": [
      {
        "pageNo": 1,
        "text": "员工编号：EMP-001",
        "confidence": 0.98,
        "bbox": [32, 68, 272, 104]
      }
    ],
    "fields": [
      {
        "fieldCode": "employee_name",
        "value": "张三",
        "confidence": 0.98,
        "bbox": [32, 68, 272, 104],
        "pageNo": 1,
        "validationStatus": "PASSED",
        "reviewRequired": false,
        "validationMessage": null
      }
    ]
  }
}
```

## OCR 引擎与配置

默认引擎是 `paddle`。项目根目录的 `.env`、`.env.example` 和 PyCharm 的 `Local OCR Service` 配置均已设置为真实 PaddleOCR：

```dotenv
OCR_ENGINE=paddle
OCR_DATA_DIR=./data
OCR_LEGACY_DATA_DIR=./app/data
OCR_MAX_FILE_SIZE_BYTES=10485760
OCR_MAX_IMAGE_EDGE=4096
OCR_LOW_CONFIDENCE_THRESHOLD=0.85
OCR_HOST=127.0.0.1
OCR_PORT=8000
MINIO_ENDPOINT=http://127.0.0.1:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=change-me-minio-password
MINIO_BUCKET=hr-platform
MINIO_ALLOWED_SOURCE_PREFIX=archive/
OCR_INTERNAL_TOKEN=local-ocr-internal-token
```

当前本地 Compose 端口映射为 API `19000`、控制台 `19001` 时，将 `MINIO_ENDPOINT` 覆盖为 `http://127.0.0.1:19000`。

`OCR_MAX_IMAGE_EDGE` 限制送入引擎图片的最长边，默认 4096。超大图会等比例缩放以控制本地内存使用，但响应坐标仍以原图为准。

`OCR_LOW_CONFIDENCE_THRESHOLD` 的默认值为 `0.85`。员工档案映射字段低于该置信度时会返回 `reviewRequired: true`；格式校验失败也会触发该标记。

`mock` 仅用于测试或前后端接口联调。需要使用它时显式设置 `OCR_ENGINE=mock`；该引擎不会执行真实识别，也不会生成业务字段。

## 测试

```bash
.venv/bin/pytest -q
```

服务将任务元数据和字段修订记录保存到 `OCR_DATA_DIR/ocr_tasks.sqlite3`，内部对象引用保存在同一 SQLite 表的专用列中，因此服务重启后仍可按 `taskId` 查询任务、文本块、失败状态、原图元数据和修订历史。对象 key 不出现在公开任务 JSON 中。

档案服务调用 OCR 时不重复发送原图字节，而是提交 MinIO 对象引用。对象引用接口要求 `X-OCR-Internal-Token`，并只允许读取 `MINIO_ALLOWED_SOURCE_PREFIX` 下的对象：

```bash
curl -X POST http://127.0.0.1:8000/api/ocr/tasks \
  -H 'X-OCR-Internal-Token: local-ocr-internal-token' \
  -F sourceObjectKey=archive/employee/1/profile.png \
  -F originalName=profile.png \
  -F contentType=image/png \
  -F size=12345 \
  -F sha256=<sha256> \
  -F documentType=employee_profile
```

迁移旧目录时先执行只读报告：

```bash
.venv/bin/python scripts/migrate_local_storage.py \
  --data-dir data --legacy-data-dir app/data --dry-run
```

确认报告中所有源文件都能校验后，再执行不删除源文件的迁移；只有需要清理且已完成回读校验时，才显式追加 `--delete-local-binaries`。SQLite 文件不会被脚本删除。

任务列表按创建时间倒序返回，结构为 `items`、`total`、`page` 和 `pageSize`。统计接口返回总任务数、成功/失败任务数、包含待复核字段的任务数、人工修订总数，以及失败原因的聚合计数。

`POST /api/ocr/evaluations` 用于论文实验与演示统计：调用方应准备高、中、低清晰度的脱敏样本及人工字段真值，并分别以 `HIGH`、`MEDIUM`、`LOW` 传入。响应中的 `fieldAccuracy` 是与真值完全一致的字段数除以真值字段数，`fieldDetectionRate` 是已返回字段数除以真值字段数，`failureRate` 按 OCR 任务状态统计；`byClarity` 提供同一指标的清晰度分组结果。
