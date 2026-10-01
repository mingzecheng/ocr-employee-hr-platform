# 档案工作台

Vue 3 + Vite + TypeScript 管理端，默认只访问网关提供的 `/api` 路径。

## 本地运行

```bash
npm install
npm run dev
```

开发服务器默认把 `/api` 代理到 `http://127.0.0.1:18080`。如网关使用其他地址，设置 `VITE_GATEWAY_URL`；生产环境可通过 `VITE_API_BASE_URL` 指定 API 前缀或网关地址。

生产构建只检查前端资源，不会启动后端服务：

```bash
npm test -- --run
npm run build
```

## 已覆盖流程

- JWT 登录、持久化和 401 清理；
- 员工列表与身份证号脱敏展示；
- PNG/JPEG 档案材料上传；
- OCR 文本块、字段置信度、字段人工修订；
- 检测框效果图代理加载、缺失状态和下载。
