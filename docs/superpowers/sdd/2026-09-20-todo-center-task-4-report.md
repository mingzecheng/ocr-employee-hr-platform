# P1 待办中心 Task 4 实施报告

日期：2026-09-20
模块：gateway-service、frontend

## 状态

已完成。待办接口已通过 gateway 暴露，Vue 管理端新增 Dashboard 摘要、待办中心页面、导航入口和响应式状态展示。

## 实现内容

- gateway 新增 `workflow-todos` 路由，将 `/api/todos/**` 转发到 workflow-service。
- frontend 新增 `getTodos(limit = 50)`、TodoData 类型和四类 TodoItem 类型约束。
- Dashboard 独立加载员工、统计和待办请求；待办接口失败不会隐藏员工或统计数据。
- 新增 `/app/todos` 页面，覆盖 loading、error、empty、四类待办、优先级样式和键盘可访问跳转。
- 导航新增待办中心及成功请求后的数量徽标；徽标请求失败时不显示。
- 维持前端只访问 gateway 的边界，条目跳转使用服务返回的 `targetPath`。

## TDD RED

先增加 gateway 路由断言和 frontend focused tests，再运行：

```bash
cd frontend
npm test -- --run src/components/TodoSummary.test.ts src/views/TodoView.test.ts src/views/DashboardView.test.ts

cd ..
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -f backend/pom.xml -pl gateway-service -am \
  -Dsurefire.failIfNoSpecifiedTests=false -Dtest=GatewayRouteConfigurationTest test
```

关键 RED 结果：gateway 路由列表缺少 `workflow-todos`；前端两个新增组件因文件不存在而无法解析，Dashboard 摘要断言未满足。该失败对应缺失功能，而非测试配置错误。

## GREEN

focused frontend 验证：

```bash
cd frontend
npm test -- --run src/components/TodoSummary.test.ts src/views/TodoView.test.ts src/views/DashboardView.test.ts
```

结果：4 个测试文件、7 个测试通过。

gateway focused 结果：`GatewayRouteConfigurationTest` 1/1 通过，`BUILD SUCCESS`。

前端全量验证：

```bash
cd frontend
npm test -- --run
npm run build
```

结果：7 个测试文件、14 个测试通过；`vue-tsc --noEmit` 和 Vite production build 均通过。构建仅输出现有 Element Plus 主包超过 500 kB 的优化提示。

## 回归结果

- gateway-service 全量：3 个测试通过，Failures 0、Errors 0、Skipped 0。
- workflow todo focused：在补充 OCR 错误详情脱敏后 16 个测试通过。
- frontend 全量：13 个测试通过，生产构建成功。

## 变更文件

- `backend/gateway-service/src/main/resources/application.yml`
- `backend/gateway-service/src/test/java/com/hrplatform/gateway/GatewayRouteConfigurationTest.java`
- `frontend/src/api/todos.ts`
- `frontend/src/types/api.ts`
- `frontend/src/components/TodoSummary.vue`
- `frontend/src/components/TodoSummary.test.ts`
- `frontend/src/views/TodoView.vue`
- `frontend/src/views/TodoView.test.ts`
- `frontend/src/views/DashboardView.vue`
- `frontend/src/views/DashboardView.test.ts`
- `frontend/src/layouts/AppLayout.vue`
- `frontend/src/layouts/AppLayout.test.ts`
- `frontend/src/router/index.ts`
- `frontend/src/styles/index.css`
- `docs/superpowers/sdd/2026-09-20-todo-center-task-4-report.md`

## 自审与遗留风险

- UI 使用现有无圆角工作台风格和 Element Plus 图标，条目支持鼠标、键盘 Enter 和明确 focus 状态。
- Dashboard 使用 `Promise.allSettled` 独立加载各数据源，待办错误不会覆盖已有业务状态。
- 导航徽标请求 `limit=100`，避免使用 `limit=1` 时把返回条数误显示为全量待办数；对应回归测试覆盖调用参数和徽标显示。
- 当前前端构建保留 Element Plus 主包体积提示；不影响类型检查、测试或生产构建。
- 真实服务端到端待办验证已在 Task 5 由 `scripts/verify-platform.sh` 完成。
