<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRight, CircleCheck, Collection, DocumentChecked, Reading, Timer, Warning, WarningFilled } from '@element-plus/icons-vue'
import { request, type ContractRecord, type Page, type StatisticsOverview, type StatisticsReport, type TodoData, type TodoItem, type TodoType } from '../api/http'

const loading = ref(true)
const error = ref('')
const todoLoading = ref(true)
const todoError = ref('')
const stats = ref<StatisticsOverview>({ employeeCount: 0, completeArchiveCount: 0, archiveCount: 0, archiveCompleteness: 0, workflowCount: 0, pendingWorkflowCount: 0, activeAccessCount: 0, abnormalAccessCount: 0, ocrSucceededCount: 0, ocrFailedCount: 0, ocrReviewCount: 0, averageOcrDurationMs: 0, generatedAt: '' })
const report = ref<StatisticsReport>({ monthlyTrend: [], departments: [], workflowCycle: { completedCount: 0, averageDurationHours: 0, shortestDurationHours: 0, longestDurationHours: 0 } })
const emptyTodos = (): TodoData => ({ items: [], total: 0, pendingApprovalCount: 0, dueSoonCount: 0, overdueCount: 0, ocrFailedCount: 0, generatedAt: '' })
const todos = ref<TodoData>(emptyTodos())
const dueContractCount = ref<number | null>(null)
async function load() {
  loading.value = true; todoLoading.value = true; error.value = ''; todoError.value = ''
  const [statisticsResult, reportResult, todosResult, contractsResult] = await Promise.allSettled([
    request<StatisticsOverview>({ url: '/statistics/overview' }),
    request<StatisticsReport>({ url: '/statistics/report' }),
    request<TodoData>({ url: '/todos', params: { limit: 100 } }),
    request<Page<ContractRecord>>({ url: '/contracts', params: { status: 'ACTIVE', renewalStatus: 'DUE', page: 1, pageSize: 1 } }),
  ])
  if (statisticsResult.status === 'fulfilled') stats.value = statisticsResult.value
  else { error.value = statisticsResult.reason instanceof Error ? statisticsResult.reason.message : '统计数据暂时不可用'; ElMessage.error(error.value) }
  if (reportResult.status === 'fulfilled') report.value = reportResult.value
  if (todosResult.status === 'fulfilled' && Array.isArray(todosResult.value?.items)) todos.value = todosResult.value
  else { todos.value = emptyTodos(); todoError.value = todosResult.status === 'rejected' && todosResult.reason instanceof Error ? todosResult.reason.message : '待办数据暂时不可用' }
  dueContractCount.value = contractsResult.status === 'fulfilled' ? contractsResult.value.total : null
  loading.value = false; todoLoading.value = false
}
function todoTypeLabel(type: TodoType) { return { HR_REQUEST_APPROVAL: '人事流程审批', ARCHIVE_ACCESS_APPROVAL: '档案授权审批', ARCHIVE_RETURN_DUE: '档案归还', OCR_FAILED: 'OCR 识别异常' }[type] }
function todoIcon(type: TodoType) { return { HR_REQUEST_APPROVAL: DocumentChecked, ARCHIVE_ACCESS_APPROVAL: Collection, ARCHIVE_RETURN_DUE: Timer, OCR_FAILED: WarningFilled }[type] }
function todoText(item: TodoItem) { return item.title || todoTypeLabel(item.type) }
onMounted(load)
</script>

<template>
  <div class="page-head dashboard-head"><div><span class="section-kicker">HR OPERATIONS / 现场状态</span><h1>人事运营总览</h1><p>审批与交接、OCR 复核、合同续签和档案异常集中处理。</p></div><div class="dashboard-flag"><span>TODAY'S BRIEF</span><strong>人事运营 · 实时同步</strong><small>原图、修订与审批轨迹均可追溯</small></div><el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Reading /></el-icon>刷新数据</el-button></div>
  <el-alert v-if="error" type="warning" :closable="false" show-icon title="统计接口暂时不可用，仍可继续浏览其他模块" class="inline-alert" />
  <div class="metric-grid" v-loading="loading"><article class="metric-card ink"><span class="metric-label">待处理审批</span><strong>{{ todoError ? '—' : todos.pendingApprovalCount }}</strong><small>人事流程与档案授权</small><el-icon><DocumentChecked /></el-icon></article><article class="metric-card"><span class="metric-label">OCR 待复核</span><strong>{{ stats.ocrReviewCount + stats.ocrFailedCount }}</strong><small>待复核 {{ stats.ocrReviewCount }} · 识别失败 {{ stats.ocrFailedCount }}</small><el-icon><WarningFilled /></el-icon></article><article class="metric-card"><span class="metric-label">合同待续签</span><strong>{{ dueContractCount ?? '—' }}</strong><small>{{ dueContractCount === null ? '合同台账暂不可用' : '未来 60 天内到期' }}</small><el-icon><Timer /></el-icon></article><article class="metric-card risk"><span class="metric-label">异常授权</span><strong>{{ stats.abnormalAccessCount }}</strong><small>逾期归还 {{ todos.overdueCount }} 项</small><el-icon><Warning /></el-icon></article></div>
  <div class="dashboard-columns"><section class="surface-panel"><div class="panel-heading"><div><span class="section-kicker">OCR QUALITY</span><h2>OCR 识别质量</h2></div><RouterLink to="/employees" class="text-link">查看档案 <el-icon><ArrowRight /></el-icon></RouterLink></div><div class="quality-row"><div class="donut" :style="{ '--score': `${stats.ocrSucceededCount + stats.ocrFailedCount ? Math.round(stats.ocrSucceededCount / (stats.ocrSucceededCount + stats.ocrFailedCount) * 100) : 0}%` }"><span>{{ stats.ocrSucceededCount + stats.ocrFailedCount ? Math.round(stats.ocrSucceededCount / (stats.ocrSucceededCount + stats.ocrFailedCount) * 100) : 0 }}<small>%</small></span></div><div class="quality-list"><div><span class="dot green" />识别成功<strong>{{ stats.ocrSucceededCount }}</strong></div><div><span class="dot amber" />需要复核<strong>{{ stats.ocrReviewCount }}</strong></div><div><span class="dot red" />识别失败<strong>{{ stats.ocrFailedCount }}</strong></div></div></div></section><section class="surface-panel attention-panel"><div class="panel-heading"><div><span class="section-kicker">ACTION QUEUE</span><h2>待处理事项</h2></div><RouterLink to="/todos" class="text-link">查看待办 <el-icon><ArrowRight /></el-icon></RouterLink></div><div v-if="todoError" class="queue-empty">{{ todoError }}</div><div v-else-if="!todos.items.length" class="queue-empty"><span class="queue-icon blue"><CircleCheck /></span><div><strong>当前范围没有待处理事项</strong><small>新的审批或异常会在这里出现</small></div></div><template v-else><RouterLink v-for="item in todos.items.slice(0, 3)" :key="`${item.type}-${item.id}`" :to="item.targetPath" class="queue-item"><span class="queue-icon" :class="item.priority === 'HIGH' ? 'red' : item.priority === 'MEDIUM' ? 'amber' : 'blue'"><component :is="todoIcon(item.type)" /></span><div><strong>{{ todoText(item) }}</strong><small>{{ todoTypeLabel(item.type) }} · {{ item.status.replace(/_/g, ' ') }}</small></div></RouterLink></template></section></div>
  <section class="dashboard-report-grid">
    <section class="surface-panel report-panel"><div class="panel-heading"><div><span class="section-kicker">MONTHLY TREND</span><h2>业务趋势</h2></div><span class="filter-total">按月聚合</span></div><div v-if="!report.monthlyTrend.length" class="report-empty">当前范围暂无趋势数据</div><div v-else class="trend-list"><div v-for="point in report.monthlyTrend.slice(-6)" :key="point.period" class="trend-row"><strong>{{ point.period }}</strong><span class="trend-bar"><i :style="{ width: `${Math.min(100, point.workflowCount * 12 + point.employeeCount * 5)}%` }" /></span><small>员工 {{ point.employeeCount }} · 流程 {{ point.workflowCount }} · OCR {{ point.ocrSucceededCount + point.ocrFailedCount }}</small></div></div></section>
    <section class="surface-panel report-panel"><div class="panel-heading"><div><span class="section-kicker">WORKFLOW CYCLE</span><h2>流程处理周期</h2></div><span class="filter-total">已完成 {{ report.workflowCycle.completedCount }} 笔</span></div><div class="cycle-metrics"><div><strong>{{ report.workflowCycle.averageDurationHours.toFixed(1) }}h</strong><small>平均处理时长</small></div><div><strong>{{ report.workflowCycle.shortestDurationHours.toFixed(1) }}h</strong><small>最短处理时长</small></div><div><strong>{{ report.workflowCycle.longestDurationHours.toFixed(1) }}h</strong><small>最长处理时长</small></div></div></section>
    <section class="surface-panel report-panel report-departments"><div class="panel-heading"><div><span class="section-kicker">DEPARTMENT VIEW</span><h2>部门业务对比</h2></div><span class="filter-total">当前授权范围</span></div><div v-if="!report.departments.length" class="report-empty">当前范围暂无部门数据</div><el-table v-else :data="report.departments.slice(0, 6)" size="small"><el-table-column prop="departmentName" label="部门" min-width="150" /><el-table-column prop="employeeCount" label="员工" width="76" /><el-table-column prop="workflowCount" label="流程" width="76" /><el-table-column prop="pendingWorkflowCount" label="待处理" width="86" /><el-table-column prop="abnormalAccessCount" label="异常授权" width="96" /></el-table></section>
  </section>
</template>
