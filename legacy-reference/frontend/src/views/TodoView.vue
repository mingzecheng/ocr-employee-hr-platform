<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">Queue / Work items</p>
        <h1>待办中心</h1>
        <p class="heading-copy">集中处理审批、归还和识别异常。</p>
      </div>
      <el-button text :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div v-if="loading" class="paper-panel empty-state">正在加载待办中心</div>
    <div v-else-if="error" class="dashboard-error todo-page-error">
      <span>{{ error }}</span>
      <el-button text :icon="Refresh" @click="load">重新加载</el-button>
    </div>
    <div v-else-if="!data?.items.length" class="paper-panel empty-state">
      暂无待处理事项
    </div>
    <section v-else class="paper-panel todo-list-panel" aria-label="待办列表">
      <div class="panel-heading todo-list-heading">
        <div>
          <h2>当前事项</h2>
          <span class="toolbar-note">共 {{ data.total }} 项</span>
        </div>
        <div class="todo-list-counts">
          <span>{{ data.pendingApprovalCount }} 待审批</span>
          <span>{{ data.ocrFailedCount }} OCR 失败</span>
        </div>
      </div>
      <div class="todo-list">
        <article
          v-for="item in data.items"
          :key="`${item.type}-${item.id}`"
          class="todo-item"
          :class="priorityClass(item.priority)"
          tabindex="0"
          role="link"
          :data-test="`todo-item-${item.id}`"
          @click="openItem(item.targetPath)"
          @keydown.enter.prevent="openItem(item.targetPath)"
        >
          <span class="todo-item-icon" aria-hidden="true">
            <el-icon><component :is="iconFor(item.type)" /></el-icon>
          </span>
          <div class="todo-item-main">
            <div class="todo-item-topline">
              <strong>{{ typeLabel(item.type) }}</strong>
              <span class="todo-priority" :class="priorityClass(item.priority)">{{ priorityLabel(item.priority) }}</span>
            </div>
            <div class="todo-item-title">{{ item.title || typeLabel(item.type) }}</div>
            <div class="todo-item-meta">
              <span>{{ statusLabel(item.status) }}</span>
              <span v-if="item.dueAt">{{ dueLabel(item.dueAt) }} {{ formatDate(item.dueAt) }}</span>
              <span v-else>{{ formatDate(item.createdAt) }}</span>
            </div>
          </div>
          <el-icon class="todo-item-arrow" aria-hidden="true"><ArrowRight /></el-icon>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Collection, DocumentChecked, Timer, WarningFilled, Refresh } from '@element-plus/icons-vue'
import { getTodos } from '@/api/todos'
import type { TodoData, TodoItem, TodoPriority, TodoType } from '@/types/api'

const router = useRouter()
const data = ref<TodoData | null>(null)
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await getTodos()
  } catch {
    data.value = null
    error.value = '待办数据暂时无法加载'
  } finally {
    loading.value = false
  }
}

function openItem(targetPath: string) {
  router.push(targetPath)
}

function typeLabel(type: TodoType) {
  return {
    HR_REQUEST_APPROVAL: 'HR 审批',
    ARCHIVE_ACCESS_APPROVAL: '档案访问审批',
    ARCHIVE_RETURN_DUE: '归还到期',
    OCR_FAILED: 'OCR 识别失败',
  }[type]
}

function priorityLabel(priority: TodoPriority) {
  return { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }[priority]
}

function statusLabel(status: string) {
  return {
    PENDING_DEPT_APPROVAL: '部门审批中',
    PENDING_HR_APPROVAL: '人事复核中',
    PENDING_HR_REVIEW: '人事复核中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    IN_USE: '使用中',
    OVERDUE: '已逾期',
    FAILED: '识别失败',
  }[status] || status.replaceAll('_', ' ')
}

function priorityClass(priority: TodoPriority) {
  return `priority-${priority.toLowerCase()}`
}

function iconFor(type: TodoType) {
  return {
    HR_REQUEST_APPROVAL: DocumentChecked,
    ARCHIVE_ACCESS_APPROVAL: Collection,
    ARCHIVE_RETURN_DUE: Timer,
    OCR_FAILED: WarningFilled,
  }[type]
}

function dueLabel(value: string) {
  return new Date(value).getTime() < Date.now() ? '已逾期' : '截止'
}

function formatDate(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间未记录'
}

onMounted(load)
</script>
