# 档案资源工作台设计

## 1. 背景与目标

当前平台已经支持员工创建、图片上传、OCR 识别、文本块展示、检测框效果图和字段修订，但前端无法查看一个员工已有的材料集合、版本历史或原始文件。档案资源工作台补齐“材料进入档案后可查询、可追溯、可受控使用”的业务链路。

本阶段目标：

- 通过 archive-service 查询员工名下的档案材料和材料版本；
- 在数据范围校验通过后读取 MinIO 中的原始文件；
- 通过网关向 Vue 返回原图预览或下载响应；
- 在员工档案页展示材料、版本、文件摘要和 OCR 入口；
- 保持现有微服务边界、MySQL 读写分离、Redis 不保存文件内容的约束。

## 2. 范围边界

本阶段包含材料列表、版本列表、文件预览/下载、scope 校验、前端材料面板和端到端验收。

本阶段不包含版本发布状态机、材料删除、批量导出、在线文档编辑和 workflow 数据范围快照。这些行为需要独立的状态设计，不在下载接口中隐式实现。

## 3. 服务与数据流

```text
Vue 档案页
    -> Gateway
       -> archive-service
          -> scope-aware MyBatis 查询 archive_db 只读副本
          -> scope 校验通过后读取 MinIO
          -> 返回文件流或 JSON 元数据
```

`archive_document`、`archive_version` 和 `file_object` 已经包含本阶段所需字段，不新增迁移。材料和版本查询必须通过员工归属连接 `archive_record -> employee`，不能仅凭客户端传入的 documentId/versionId 查询。

读接口使用 `@Transactional(readOnly = true)`，由公共路由数据源选择副本；下载前的归属查询和对象读取属于同一个业务服务操作。上传、OCR、字段修订等现有写接口保持主库事务。

## 4. HTTP 接口

### 查询员工材料

`GET /api/archive/employees/{employeeId}/documents`

返回当前 scope 可见的材料摘要：

```json
{
  "id": 21,
  "employeeId": 7,
  "documentType": "EMPLOYEE_PROFILE",
  "title": "profile.png",
  "status": "ACTIVE",
  "latestVersionId": 31,
  "latestVersionNo": 1,
  "latestFileName": "profile.png",
  "latestContentType": "image/png",
  "latestSize": 2048,
  "createdAt": "2026-09-18T14:00:00"
}
```

无权访问员工时返回 `403/FORBIDDEN`，不返回空列表掩盖资源越权。

### 查询材料版本

`GET /api/archive/documents/{documentId}/versions`

返回版本号、状态、文件名、类型、大小、SHA-256、创建人和创建时间。查询必须再次通过 scope 过滤材料归属，不能信任前一个列表请求的结果。

### 预览或下载版本

`GET /api/archive/versions/{versionId}/download`

服务先调用 `findVersionByIdInScope`，通过后才调用 `ObjectStorage.get`。响应使用数据库中的文件类型和安全处理后的原文件名设置 `Content-Type` 与 `Content-Disposition: inline`，前端通过 blob URL 预览；用户主动下载时由浏览器使用同一个受保护接口保存文件。

资源不存在、scope 不允许和对象存储失败分别保持稳定错误语义：`403/FORBIDDEN`、`404/ARCHIVE_NOT_FOUND`、`502/STORAGE_ERROR`。接口不接受 object key，避免客户端构造对象存储路径。

## 5. 后端设计

`ArchiveMapper` 增加两个 scope-aware 查询：

- `listDocumentsByEmployeeInScope(employeeId, scopeType, employeeScopeId, departmentId)`；
- `listVersionsByDocumentInScope(documentId, scopeType, employeeScopeId, departmentId)`。

查询结果使用独立 DTO，不直接序列化 MyBatis 实体。`ArchiveService` 增加：

- `listDocuments(long employeeId, DataScope scope)`；
- `listVersions(long documentId, DataScope scope)`；
- `downloadVersion(long versionId, DataScope scope)`。

下载返回内部 `DownloadData`，包含原始文件名、媒体类型和内容字节；Controller 负责构造 HTTP headers，不把存储 key 返回给前端。现有上传响应仍可保留 objectKey 供内部验收，但新查询和下载响应不暴露存储路径。

## 6. 前端设计

员工档案页增加材料面板：

- 显示材料类型、标题、最新版本、文件类型、大小和状态；
- 展开材料后显示版本列表；
- 提供原图预览和下载按钮；
- 保留现有上传、OCR、文本块、字段修订和检测框效果图区域；
- 上传或 OCR 成功后刷新材料列表；
- 404、403、文件预览失败和空材料列表分别显示明确状态。

前端只调用 `/api/archive/**`，通过 Axios 自动携带 JWT。文件响应使用 blob URL，并在组件销毁或替换时释放 URL，避免重复预览造成内存泄漏。

## 7. 测试与验收

- Service 单测：scope 传参、材料摘要转换、版本查询、越权时不访问 MinIO、下载 headers 所需数据完整；
- MySQL 持久化测试：同一员工材料和版本可查询，其他部门 scope 查不到；
- Controller 测试：三个新路径返回 JSON 或图片/文件响应，异常码符合契约；
- 前端测试：材料列表、空状态、版本展开、预览失败和下载事件；
- 真实验收：管理员创建员工并上传图片，网关查询材料和版本，下载原图并校验内容类型/字节数；员工或部门负责人访问其他范围的 versionId 必须返回 `403/FORBIDDEN`。

## 8. 约束审核结论

该设计复用现有数据库结构，不跨服务读取 identity/workflow 表，不修改 OCR SQLite，不把文件内容写入 Redis。它与 `docs/数据库设计.md` 的读写分离、对象存储和数据范围规则一致，可以进入测试驱动实现阶段。
