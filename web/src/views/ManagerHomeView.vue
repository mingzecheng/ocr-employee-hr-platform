<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, Bell, CircleCheck, Refresh, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { request, type InventoryTask, type Page, type StatisticsOverview, type TodoData } from '../api/http'

const loading = ref(false)
const stats = ref<StatisticsOverview | null>(null)
const todos = ref<TodoData | null>(null)
const tasks = ref<Page<InventoryTask> | null>(null)
const openTasks = computed(() => tasks.value?.items.filter((task) => !['COMPLETED', 'ARCHIVED'].includes(task.status)).length ?? 0)
async function load() {
  loading.value = true
  const results = await Promise.allSettled([
    request<StatisticsOverview>({ url: '/statistics/overview' }),
    request<TodoData>({ url: '/todos', params: { limit: 8 } }),
    request<Page<InventoryTask>>({ url: '/inventory-tasks', params: { page: 1, pageSize: 20 } }),
  ])
  if (results[0].status === 'fulfilled') stats.value = results[0].value
  if (results[1].status === 'fulfilled') todos.value = results[1].value
  if (results[2].status === 'fulfilled') tasks.value = results[2].value
  if (results.every((result) => result.status === 'rejected')) ElMessage.error('部门工作台暂时无法加载，请稍后重试')
  loading.value = false
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><span class="section-kicker">MANAGER / TEAM OPERATIONS</span><h1>团队概览</h1><p>优先处理本部门审批、档案归还与盘点差异。</p></div><el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></div>
  <section class="portal-metric-grid" v-loading="loading">
    <article><span>部门员工</span><strong>{{ stats?.employeeCount ?? '—' }}</strong><small>当前部门范围</small><el-icon><CircleCheck /></el-icon></article>
    <article><span>待审批</span><strong>{{ todos?.pendingApprovalCount ?? '—' }}</strong><small>部门流程和档案申请</small><el-icon><Bell /></el-icon></article>
    <article class="risk"><span>逾期归还</span><strong>{{ todos?.overdueCount ?? '—' }}</strong><small>需要跟进</small><el-icon><Warning /></el-icon></article>
    <article><span>进行中盘点</span><strong>{{ openTasks }}</strong><small>部门盘点任务</small><el-icon><Refresh /></el-icon></article>
  </section>
  <section class="portal-home-columns">
    <section class="surface-panel portal-home-panel">
      <div class="panel-heading"><div><span class="section-kicker">TEAM QUEUE</span><h2>部门审批与待办</h2></div><RouterLink to="/todos" class="text-link">进入待办 <el-icon><ArrowRight /></el-icon></RouterLink></div>
      <div v-if="!todos?.items.length" class="portal-empty">当前没有待处理事项</div>
      <RouterLink v-for="item in todos?.items.slice(0, 6)" :key="`${item.type}-${item.id}`" :to="item.targetPath" class="portal-task-row"><span>{{ item.title || '待办事项' }}</span><small>{{ item.status.replace(/_/g, ' ') }}</small><el-icon><ArrowRight /></el-icon></RouterLink>
    </section>
    <section class="surface-panel portal-shortcuts">
      <div class="panel-heading"><div><span class="section-kicker">TEAM RESOURCES</span><h2>部门工作</h2></div></div>
      <RouterLink to="/employees"><el-icon><CircleCheck /></el-icon><span><strong>部门员工与档案</strong><small>按当前部门范围查询</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
      <RouterLink to="/inventory"><el-icon><Warning /></el-icon><span><strong>盘点与异常</strong><small>核对版本并关闭差异</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
      <RouterLink to="/archive-access"><el-icon><Bell /></el-icon><span><strong>档案访问记录</strong><small>申请、使用与归还</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
    </section>
  </section>
</template>
