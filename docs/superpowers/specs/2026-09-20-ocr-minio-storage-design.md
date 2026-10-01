# OCR 与 MinIO 统一对象存储设计

> 日期：2026-09-20  
> 状态：已实施
> 范围：消除 OCR 本地图片目录与档案 MinIO 对象之间的持久化冗余

## 1. 背景与目标

当前档案上传链路已经把原图保存到 MinIO，但 `archive-service` 触发 OCR 时又把原图字节发送给 `ocr-service`，OCR 服务再写入 `ocr-service/data`。OCR 检测框预览也写入本地目录，因此同一份原图存在两套持久化位置，并且当前工程还保留了 `ocr-service/app/data` 旧目录。

本次改造目标：

1. MinIO 成为所有 OCR 二进制证据的唯一持久化介质。
2. OCR 服务本地只保留 SQLite 任务、识别结果和字段修订元数据，不保留原图或检测框 PNG。
3. 档案服务与 OCR 服务之间复用已存在的 MinIO 原图对象，不重复上传。
4. 保持现有 OCR 查询、检测框预览、字段修订和 archive-service 对接接口兼容。
5. 为已有 `data` 与 `app/data` 历史任务提供可重复、可校验、可回滚的迁移流程，迁移完成前不删除原文件。

## 2. 存储边界

### 2.1 MinIO 对象布局

同一个 `MINIO_BUCKET`（当前为 `hr-platform`）中使用不同前缀：

| 前缀 | 内容 | 写入方 |
|---|---|---|
| `archive/employee/{employeeId}/...` | 档案服务上传的原始档案文件 | archive-service |
| `ocr/source/{sha256}.{ext}` | 独立调用 OCR 上传的原图；按内容哈希去重 | ocr-service |
| `ocr/preview/{taskId}.png` | OCR 检测框效果图 | ocr-service |

档案服务已有对象的实际 key 通过内部调用传给 OCR。对于档案业务链路，OCR 只读取 `archive/...` 对象，不再创建 `ocr/source/...` 副本。直接调用 OCR 上传文件时，OCR 才写入 `ocr/source/...`。

### 2.2 OCR 本地目录

迁移完成后的 `OCR_DATA_DIR` 只包含：

```text
ocr-service/data/ocr_tasks.sqlite3
```

不再创建或依赖：

```text
ocr-service/data/*.png
ocr-service/data/previews/*.png
ocr-service/app/data/*.png
ocr-service/app/data/previews/*.png
ocr-service/app/data/ocr_tasks.sqlite3
```

`ocr-service/app/data` 只作为迁移期间的只读历史输入，迁移成功后移除 `OCR_LEGACY_DATA_DIR` 配置。

## 3. 请求与数据流

### 3.1 档案业务 OCR

```text
前端上传图片
  -> archive-service
  -> 原图写入 MinIO archive/...
  -> archive_db.file_object 保存 object_key 和 sha256
  -> archive-service 调用 OCR，传递 sourceObjectKey、文件名、MIME、大小、SHA-256
  -> ocr-service 从 MinIO 临时下载到受控临时文件
  -> PaddleOCR 识别
  -> 识别结果写入 SQLite
  -> 检测框写入 MinIO ocr/preview/{taskId}.png
  -> 删除临时文件
```

`archive-service` 不再调用 `ObjectStorage.get()` 后把完整原图字节放入 OCR HTTP multipart 请求。原图只有一个长期保存对象。

### 3.2 独立 OCR 上传

继续支持 `POST /api/ocr/tasks` 的 multipart 调用。OCR 接收文件后计算 SHA-256，并将文件写入 `ocr/source/{sha256}.{ext}`；相同 SHA-256 的对象直接复用。接口响应结构保持不变，不向外暴露 MinIO 凭据或完整内部对象路径。

### 3.3 检测框预览

检测框接口保持：

```text
GET /api/ocr/tasks/{taskId}/detection-preview
```

服务从 SQLite 取得预览对象引用，从 MinIO 读取并返回 `image/png`。如果任务成功但预览对象不存在，则从 MinIO 原图临时生成预览并重新写入 MinIO；原图不存在时仍返回 `DETECTION_PREVIEW_NOT_FOUND`，不能隐藏已有的 OCR 文本结果。

## 4. 接口兼容与内部扩展

### 4.1 OCR 创建任务接口

现有 `POST /api/ocr/tasks` 保持路径和响应包络不变，新增两种互斥输入模式：

- 外部上传模式：提交 `file`。
- 内部对象引用模式：提交 `sourceObjectKey`、`originalName`、`contentType`、`size` 和 `sha256`，并携带内部共享令牌。

两种模式最终都生成相同的 `OcrTask`、文本块、字段结果和检测框预览。对象引用模式只允许读取配置的 MinIO bucket，并校验 key 不为空、不能包含 `..`，且必须位于允许的 `archive/` 前缀下。

### 4.2 SQLite 内部字段

不改变对外 JSON 结构。为 `ocr_tasks` 增加内部存储字段：

- `source_bucket`、`source_object_key`；
- `preview_bucket`、`preview_object_key`；
- `storage_version`。

原有 `payload` 继续保存对外任务 JSON，内部对象 key 不通过 API 返回。SQLite 初始化和历史数据库读取都必须兼容缺少这些列的旧版本。

### 4.3 archive-service 对接

将 `OcrClient.createTask` 从“文件名 + 内容字节”改为“档案文件对象引用 + 文档类型”。`HttpOcrClient` 通过 multipart 字段发送对象引用和内部令牌；`ArchiveService` 直接使用数据库中已保存的 `objectKey`，不再读取 MinIO 文件内容。

新增配置：

```text
OCR_INTERNAL_TOKEN
```

archive-service 和 ocr-service 使用同一值。直接 multipart 上传模式不需要该令牌；对象引用模式必须校验该令牌。

## 5. 代码模块调整

### OCR 服务

- 新增 `app/object_storage.py`：封装 MinIO bucket 检查、对象上传、对象下载到临时文件、对象读取和存在性校验。
- 重构 `SourceFileStore`：只生成文件元数据和对象引用，不再向 `data` 写图片。
- 重构检测框生成：原图和预览只在系统临时目录短暂存在，预览完成后上传 MinIO 并清理临时文件。
- 扩展 `TaskStore`：保存内部对象引用，兼容旧 SQLite 表和旧任务 payload。
- 新增 `scripts/migrate_local_storage.py`：合并当前/旧 SQLite 任务记录，上传历史原图和预览到 MinIO，回写对象引用，支持 dry-run、校验和显式删除本地二进制。
- `requirements.txt` 增加固定主版本范围的 Python MinIO SDK。

### archive-service

- `OcrClient`、`HttpOcrClient` 和 `ArchiveService.runOcr` 改为对象引用契约。
- 保留 archive-service 自己的 MinIO 下载接口，确保档案原图下载行为不变。
- 新增 OCR 内部令牌配置和请求头转发。

### 配置与文档

- `ocr-service/.env.example` 增加 `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`、`OCR_INTERNAL_TOKEN`。
- `backend/archive-service/src/main/resources/application.yml` 增加 `OCR_INTERNAL_TOKEN`。
- 更新 OCR README、后端 README、数据库设计和数据契约，明确 SQLite 只存元数据、MinIO 存二进制。

## 6. 历史迁移与删除策略

迁移命令默认只检查和上传，不删除源文件：

1. 停止 OCR 服务或确保没有写入任务。
2. 备份 `ocr-service/data` 和 `ocr-service/app/data`。
3. 执行 dry-run，输出任务数、源文件数、预览数、缺失文件和 SHA-256 冲突。
4. 执行迁移，将历史文件上传 MinIO，合并任务元数据并回写 SQLite 对象引用。
5. 重新启动 OCR，逐条抽样验证任务查询和检测框预览。
6. 执行 `--delete-local-binaries`，只删除已经成功上传且可通过 MinIO 读取的 PNG/JPEG 文件。
7. 保留 `ocr_tasks.sqlite3`；确认所有旧任务已经合并后，才删除 `ocr-service/app/data`。

迁移失败时不删除任何本地文件；迁移命令必须可重复执行，已存在且 SHA-256 一致的对象跳过上传。

## 7. 错误处理与安全

- MinIO 不可用时，不能创建一个缺少原图证据的成功任务；返回明确的 `STORAGE_UNAVAILABLE` 或将任务记录为失败。
- 对象不存在时返回 `SOURCE_OBJECT_NOT_FOUND`；检测框预览缺失继续使用 `DETECTION_PREVIEW_NOT_FOUND`。
- 对象引用模式必须校验内部令牌、bucket、key 前缀和路径安全性。
- MinIO 密码和内部令牌只从环境变量读取，不写入 SQLite payload、接口响应或日志。
- 临时文件使用系统临时目录，识别完成、异常和进程退出路径均执行清理。

## 8. 测试与验收

### OCR 测试

- MinIO 对象存储单元测试：上传、复用、读取、缺失对象和失败降级。
- API 测试：multipart 上传后本地无图片文件、对象引用模式成功、无令牌拒绝、非法 key 拒绝。
- 任务测试：SQLite 保存对象引用但响应不暴露内部 key；任务查询从 MinIO 读取原图关联证据。
- 预览测试：预览写入 MinIO、接口返回 PNG、缺失预览可补生成、缺失原图返回原有错误码。
- 迁移测试：当前目录与旧目录合并、重复运行幂等、SHA-256 一致时不重复上传、失败时不删除源文件。

### archive-service 测试

- OCR 请求包含对象引用而不包含原图字节。
- 档案上传仍只生成一个 archive MinIO 原图对象。
- OCR 幂等、结果查询、检测框代理和原图下载保持通过。

### 平台验收

验收脚本增加以下检查：

1. 上传一张档案图片后，MinIO 中只出现一个对应原图对象和一个 OCR 预览对象。
2. `ocr-service/data` 不出现 PNG/JPEG 或 `previews` 持久化目录，只存在 SQLite。
3. OCR 结果和检测框预览可通过网关正常查询。
4. 重启 OCR 服务后历史任务仍可查询，检测框预览仍可下载。
5. OCR 服务的 MinIO 不可用时，错误状态和原图保留规则符合契约。

## 9. 不在本次范围

- 不把完整 OCR JSON 写入 MySQL 或 Redis。
- 不让 OCR 服务直接连接 archive_db；档案对象引用由 archive-service 通过内部 HTTP 合同传递。
- 不改变前端 OCR 结果和检测框预览接口。
- 不删除 MinIO 中已有的 archive 原图对象；迁移只清理已验证的本地二进制副本。
