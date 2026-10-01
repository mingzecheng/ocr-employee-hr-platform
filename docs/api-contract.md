# API 契约（重构版）

所有业务接口返回统一结构：`{ code, message, data, traceId }`。`code=0` 表示成功；`VALIDATION_ERROR`、`UNAUTHORIZED`、`FORBIDDEN`、`NOT_FOUND`、`STATE_CONFLICT` 和 `INTERNAL_ERROR` 分别对应输入、认证、范围、资源、状态和服务错误。

## 认证

`POST /api/auth/login`

```json
{"username":"demo-admin","password":"password"}
```

响应的 `data.accessToken` 作为 `Authorization: Bearer <token>` 发送。`GET /api/auth/me` 返回当前账号、角色和数据范围。

## 主要查询

| 接口 | 作用 |
| --- | --- |
| `GET /api/departments/tree` | 启用部门树 |
| `GET /api/employees` | 员工分页，支持 `keyword`、`departmentId`、`status`、`page`、`pageSize` |
| `GET /api/archive/employees/{employeeId}/documents` | 员工材料目录分页 |
| `GET /api/archive/documents/{documentId}/versions` | 材料版本分页 |
| `GET /api/hr-requests` | 入职、调动、离职流程分页 |
| `GET /api/archive-access` | 档案访问申请分页 |
| `GET /api/statistics/overview` | 按 JWT 数据范围聚合统计，可传 `from`、`to` |
| `GET /api/operation-logs` | 操作审计分页，可传动作、对象、结果和日期 |

## 档案与 OCR

`POST /api/archive/employees/{employeeId}/documents` 使用 multipart 字段 `documentType` 和 `file`，只接受 PNG/JPEG。服务器返回版本事实，不返回 MinIO 私有对象 key。

`POST /api/archive/versions/{versionId}/ocr` 触发幂等 OCR；`GET /api/archive/versions/{versionId}/ocr-result` 返回文本块、原始字段、业务映射字段、置信度、格式校验和修订历史。低于 0.85 的字段标记为复核；`PUT /api/archive/ocr-bindings/{bindingId}/fields/{fieldCode}` 保存人工修订。

## 状态操作

- 人事流程：`POST /api/hr-requests/{id}/submit|approve|reject`。
- 档案访问：`POST /api/archive-access/{id}/submit|approve|use|return`，归还请求体为 `{ "abnormal": false, "remark": "..." }`。
- 盘点：`POST /api/inventory-tasks` 创建，`/{id}/start` 启动，`/{id}/items/{itemId}/resolve` 解决差异，`/{id}/complete` 完成。

服务端在每个状态操作前重新计算数据范围，不信任客户端传入的部门或员工归属。
