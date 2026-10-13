<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Collection, DocumentChecked, Refresh, Timer, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { request, type TodoData, type TodoItem, type TodoPriority, type TodoType } from '../api/http'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const data = ref<TodoData | null>(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await request<TodoData>({ url: '/todos', params: { limit: 100 } })
  } catch (e) {
    data.value = null
    error.value = e instanceof Error ? e.message : '待办数据暂时无法加载'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

function openItem(item: TodoItem) {
  router.push(item.targetPath)
}

function typeLabel(type: TodoType) {
  return { HR_REQUEST_APPROVAL: '人事审批', ARCHIVE_ACCESS_APPROVAL: '档案授权审批', ARCHIVE_RETURN_DUE: '档案归还', OCR_FAILED: 'OCR 异常' }[type]
}

function priorityLabel(priority: TodoPriority) {
  return { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }[priority]
}

function iconFor(type: TodoType) {
  return { HR_REQUEST_APPROVAL: DocumentChecked, ARCHIVE_ACCESS_APPROVAL: Collection, ARCHIVE_RETURN_DUE: Timer, OCR_FAILED: WarningFilled }[type]
}

function priorityClass(priority: TodoPriority) { return `priority-${priority.toLowerCase()}` }
function formatDate(value?: string | null) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }).slice(0, 16) : '时间未记录' }
function dueLabel(value?: string | null) { return value && new Date(value).getTime() < Date.now() ? '已逾期' : '截止' }
function statusLabel(status: string) {
  return { PENDING_DEPT_APPROVAL: '待部门审批', PENDING_HR_APPROVAL: '待人事复核', PENDING_APPROVAL: '待授权', IN_USE: '使用中', FAILED: '识别失败' }[status] || status
}

onMounted(load)
</script>

<template>
  <div class="page-head">
    <div><span class="section-kicker">QUEUE / ACTION CENTER</span><h1>待办中心</h1><p>把审批、归还和 OCR 异常集中到一个可继续处理的队列。</p></div>
    <el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
  </div>

  <div v-if="data" class="todo-summary-strip">
    <div><span>当前待办</span><strong>{{ data.total }}</strong></div>
    <div><span>待审批</span><strong>{{ data.pendingApprovalCount }}</strong></div>
    <div><span>三日内归还</span><strong>{{ data.dueSoonCount }}</strong></div>
    <div class="risk"><span>已逾期 / OCR 失败</span><strong>{{ data.overdueCount }} / {{ data.ocrFailedCount }}</strong></div>
  </div>

  <section v-loading="loading" class="surface-panel todo-panel">
    <div v-if="error" class="todo-state">{{ error }}<el-button link type="primary" @click="load">重新加载</el-button></div>
    <div v-else-if="!data?.items.length" class="todo-state">当前范围没有待处理事项</div>
    <div v-else class="todo-list" aria-label="待办列表">
      <article v-for="item in data.items" :key="`${item.type}-${item.id}`" class="todo-item" :class="priorityClass(item.priority)" tabindex="0" role="link" @click="openItem(item)" @keydown.enter.prevent="openItem(item)">
        <span class="todo-item-icon" aria-hidden="true"><el-icon><component :is="iconFor(item.type)" /></el-icon></span>
        <div class="todo-item-main">
          <div class="todo-item-topline"><strong>{{ typeLabel(item.type) }}</strong><span class="todo-priority" :class="priorityClass(item.priority)">{{ priorityLabel(item.priority) }}</span></div>
          <div class="todo-item-title">{{ item.title || typeLabel(item.type) }}</div>
          <div class="todo-item-meta"><span>{{ statusLabel(item.status) }}</span><span v-if="item.dueAt">{{ dueLabel(item.dueAt) }} {{ formatDate(item.dueAt) }}</span><span v-else>{{ formatDate(item.createdAt) }}</span></div>
        </div>
        <el-icon class="todo-item-arrow" aria-hidden="true"><ArrowRight /></el-icon>
      </article>
    </div>
  </section>
</template>
