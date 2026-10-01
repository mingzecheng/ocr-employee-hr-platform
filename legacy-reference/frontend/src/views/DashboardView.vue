<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">Operations / Overview</p>
        <h1>今日档案概览</h1>
        <p class="heading-copy">查看员工资料完整性与最近的识别工作。</p>
      </div>
      <el-button type="primary" :icon="User" @click="router.push({ name: 'employees' })">查看员工档案</el-button>
    </div>
    <div v-if="statisticsError" class="dashboard-error">统计数据暂时无法加载</div>
    <div class="dashboard-grid">
      <div class="paper-panel stat-panel"><div class="stat-label">员工总数</div><div class="stat-value">{{ statistics ? statistics.activeEmployeeCount : '—' }}</div><div class="stat-note">当前在册资料</div></div>
      <div class="paper-panel stat-panel"><div class="stat-label">档案完整率</div><div class="stat-value">{{ statistics ? formatPercent(statistics.archiveCompletenessRate) : '—' }}</div><div class="stat-note">已发布材料的在册员工</div></div>
      <div class="paper-panel stat-panel"><div class="stat-label">OCR 识别</div><div class="stat-value">{{ statistics ? statistics.ocrTaskCount : '—' }}</div><div class="stat-note">累计识别任务</div></div>
      <div class="paper-panel stat-panel"><div class="stat-label">待处理</div><div class="stat-value">{{ statistics ? statistics.pendingReviewCount : '—' }}</div><div class="stat-note">需人工核验</div></div>
    </div>
    <section class="paper-panel quick-actions-panel">
      <div class="panel-heading">
        <div><p class="eyebrow">Workbenches</p><h2>常用操作</h2></div>
        <span class="toolbar-note">从当前业务节点继续处理</span>
      </div>
      <div class="quick-action-grid">
        <el-button type="primary" class="quick-action" data-test="quick-action-create-employee" :icon="User" @click="router.push({ name: 'employees', query: { action: 'create' } })">新建员工</el-button>
        <el-button class="quick-action" data-test="quick-action-workflow" :icon="Document" @click="router.push({ name: 'workflow' })">人事申请</el-button>
        <el-button class="quick-action" data-test="quick-action-access" :icon="Lock" @click="router.push({ name: 'archive-access' })">档案授权</el-button>
        <el-button class="quick-action" data-test="quick-action-inventory" :icon="List" @click="router.push({ name: 'inventory' })">发起盘点</el-button>
      </div>
    </section>
    <TodoSummary :data="todos" :loading="todosLoading" :error="todosError" />
    <div class="split-grid">
      <section class="paper-panel">
        <div class="panel-heading"><h2>最近员工</h2><el-button text @click="router.push({ name: 'employees' })">全部员工</el-button></div>
        <div v-if="employees.length" class="activity-list panel-body">
          <div v-for="employee in employees.slice(0, 5)" :key="employee.id" class="activity-row">
            <span><strong>{{ employee.name }}</strong> <span class="activity-meta">{{ employee.employeeNo }}</span></span>
            <span class="status-dot">在职</span>
          </div>
        </div>
        <div v-else class="empty-state">暂无员工数据</div>
      </section>
      <section v-if="statistics" class="paper-panel ocr-health-panel" data-test="ocr-health">
        <div class="panel-heading">
          <div><p class="eyebrow">OCR / Quality</p><h2>识别质量</h2></div>
          <span class="status-dot">持续统计</span>
        </div>
        <div class="health-body">
          <div class="health-score">{{ formatPercent(statistics.ocrSuccessRate) }}</div>
          <div class="health-details">
            <p>累计 {{ statistics.ocrTaskCount }} 个识别任务，成功结果可继续进行字段核验。</p>
            <div class="health-track" aria-label="OCR 成功率"><span :style="{ width: `${Math.round(statistics.ocrSuccessRate * 100)}%` }" /></div>
            <div class="health-facts"><span>{{ statistics.ocrFailedCount }} 个失败任务</span><span>{{ statistics.pendingReviewCount }} 个待复核</span></div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Document, List, Lock, User } from '@element-plus/icons-vue'
import { listEmployees } from '@/api/archive'
import { getStatisticsOverview } from '@/api/statistics'
import { getTodos } from '@/api/todos'
import TodoSummary from '@/components/TodoSummary.vue'
import type { Employee, StatisticsOverview, TodoData } from '@/types/api'

const router = useRouter()
const employees = ref<Employee[]>([])
const statistics = ref<StatisticsOverview | null>(null)
const statisticsError = ref(false)
const todos = ref<TodoData | null>(null)
const todosLoading = ref(true)
const todosError = ref('')

function formatPercent(value: number) {
  return `${Math.round(value * 100)}%`
}

onMounted(async () => {
  const [employeeResult, statisticsResult, todoResult] = await Promise.allSettled([
    listEmployees(1, 20),
    getStatisticsOverview(),
    getTodos(50),
  ])
  if (employeeResult.status === 'fulfilled') {
    employees.value = employeeResult.value.items
  } else {
    ElMessage.warning('员工列表暂时无法加载')
  }
  if (statisticsResult.status === 'fulfilled') {
    statistics.value = statisticsResult.value
  } else {
    statisticsError.value = true
    ElMessage.warning('统计数据暂时无法加载')
  }
  if (todoResult.status === 'fulfilled') {
    todos.value = todoResult.value
  } else {
    todosError.value = '待办数据暂时无法加载'
    ElMessage.warning('待办数据暂时无法加载')
  }
  todosLoading.value = false
})
</script>
