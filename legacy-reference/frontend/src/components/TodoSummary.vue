<template>
  <section class="paper-panel todo-summary" data-test="todo-summary">
    <div class="panel-heading">
      <div>
        <p class="eyebrow">Queue / Today</p>
        <h2>待办中心</h2>
      </div>
      <button type="button" class="text-action" data-test="todo-summary-link" @click="openTodoCenter">
        <el-icon><ArrowRight /></el-icon>查看全部
      </button>
    </div>
    <div v-if="loading" class="empty-state compact-empty">正在加载待办</div>
    <div v-else-if="error" class="dashboard-error todo-summary-error">{{ error }}</div>
    <div v-else-if="!data" class="empty-state compact-empty">暂无待办数据</div>
    <div v-else class="todo-summary-body">
      <div class="todo-summary-total">
        <span>当前待办</span>
        <strong>{{ data.total }}</strong>
      </div>
      <div class="todo-summary-counts" aria-label="待办分类统计">
        <span class="todo-count" :aria-label="`待审批 ${data.pendingApprovalCount}`"><b>{{ data.pendingApprovalCount }}</b>待审批</span>
        <span class="todo-count" :aria-label="`即将到期 ${data.dueSoonCount}`"><b>{{ data.dueSoonCount }}</b>即将到期</span>
        <span class="todo-count" :aria-label="`已逾期 ${data.overdueCount}`"><b>{{ data.overdueCount }}</b>已逾期</span>
        <span class="todo-count" :aria-label="`OCR 失败 ${data.ocrFailedCount}`"><b>{{ data.ocrFailedCount }}</b>OCR 失败</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import type { TodoData } from '@/types/api'

defineProps<{
  data: TodoData | null
  loading: boolean
  error: string
}>()

const router = useRouter()

function openTodoCenter() {
  router.push({ name: 'todos' })
}
</script>
