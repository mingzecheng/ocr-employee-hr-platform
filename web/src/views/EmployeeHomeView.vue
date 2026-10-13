<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRight, Bell, CircleCheck, Files, List, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { request, type StatisticsOverview, type TodoData, type TodoItem } from '../api/http'

const loading = ref(false)
const stats = ref<StatisticsOverview | null>(null)
const todos = ref<TodoData | null>(null)
async function load() {
  loading.value = true
  const [statistics, todoResult] = await Promise.allSettled([
    request<StatisticsOverview>({ url: '/statistics/overview' }),
    request<TodoData>({ url: '/todos', params: { limit: 6 } }),
  ])
  if (statistics.status === 'fulfilled') stats.value = statistics.value
  if (todoResult.status === 'fulfilled') todos.value = todoResult.value
  if (statistics.status === 'rejected' && todoResult.status === 'rejected') ElMessage.error('个人工作台暂时无法加载，请稍后重试')
  loading.value = false
}
function todoLabel(item: TodoItem) {
  return item.title || ({ ARCHIVE_RETURN_DUE: '档案归还提醒', HR_REQUEST_APPROVAL: '人事申请进度', ARCHIVE_ACCESS_APPROVAL: '档案访问申请', OCR_FAILED: 'OCR 识别异常' }[item.type] ?? '待办事项')
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><span class="section-kicker">EMPLOYEE / SELF SERVICE</span><h1>个人工作台</h1><p>查看本人申请、待办和档案访问状态。</p></div><el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></div>
  <section class="portal-metric-grid" v-loading="loading">
    <article><span>我的待办</span><strong>{{ todos?.total ?? '—' }}</strong><small>待处理事项</small><el-icon><Bell /></el-icon></article>
    <article><span>人事申请</span><strong>{{ stats?.workflowCount ?? '—' }}</strong><small>本人申请记录</small><el-icon><List /></el-icon></article>
    <article><span>待处理申请</span><strong>{{ stats?.pendingWorkflowCount ?? '—' }}</strong><small>审批流程中</small><el-icon><CircleCheck /></el-icon></article>
    <article><span>档案访问</span><strong>{{ stats?.activeAccessCount ?? '—' }}</strong><small>当前使用中</small><el-icon><Files /></el-icon></article>
  </section>
  <section class="portal-home-columns">
    <section class="surface-panel portal-home-panel">
      <div class="panel-heading"><div><span class="section-kicker">ACTION REQUIRED</span><h2>我的待办</h2></div><RouterLink to="/todos" class="text-link">全部待办 <el-icon><ArrowRight /></el-icon></RouterLink></div>
      <div v-if="!todos?.items.length" class="portal-empty">当前没有待处理事项</div>
      <RouterLink v-for="item in todos?.items.slice(0, 5)" :key="`${item.type}-${item.id}`" :to="item.targetPath" class="portal-task-row"><span>{{ todoLabel(item) }}</span><small>{{ item.status.replace(/_/g, ' ') }}</small><el-icon><ArrowRight /></el-icon></RouterLink>
    </section>
    <section class="surface-panel portal-shortcuts">
      <div class="panel-heading"><div><span class="section-kicker">QUICK ACCESS</span><h2>常用服务</h2></div></div>
      <RouterLink to="/workflows"><el-icon><List /></el-icon><span><strong>发起人事申请</strong><small>入职、调动、请假与离职</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
      <RouterLink to="/archive-access"><el-icon><Files /></el-icon><span><strong>申请档案访问</strong><small>查看申请、使用与归还记录</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
      <RouterLink to="/employees"><el-icon><CircleCheck /></el-icon><span><strong>查看本人档案</strong><small>仅显示当前账号可访问内容</small></span><el-icon class="shortcut-arrow"><ArrowRight /></el-icon></RouterLink>
    </section>
  </section>
</template>
