<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { request, type LoginLogItem } from '../api/http'

const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const items = ref<LoginLogItem[]>([])

const actionTagType: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
  LOGIN: 'success',
  LOGOUT: 'info',
  REFRESH: 'warning',
}

function dash(value?: string | null): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

function actionType(action: string): 'success' | 'info' | 'warning' | 'danger' {
  return actionTagType[action] || 'info'
}

async function load() {
  loading.value = true
  try {
    const data = await request<{ items: LoginLogItem[]; total: number }>({
      url: '/admin/login-logs',
      params: { page: page.value, size: size.value },
    })
    items.value = data.items
    total.value = data.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '登录日志加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-head">
    <div>
      <span class="section-kicker">AUTH / LOGIN TRACE</span>
      <h1>登录日志</h1>
      <p>仅汇总登录、登出与令牌刷新事件，记录账号、来源 IP 与客户端指纹；与审计日志分开维护。</p>
    </div>
    <el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
  </div>

  <section class="surface-panel table-panel">
    <el-table v-loading="loading" :data="items" class="data-table" empty-text="暂无登录记录">
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column label="用户" min-width="140">
        <template #default="scope"><strong>{{ scope.row.username }}</strong></template>
      </el-table-column>
      <el-table-column label="动作" width="120">
        <template #default="scope">
          <el-tag :type="actionType(scope.row.action)" effect="plain">{{ scope.row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="IP" width="140">
        <template #default="scope"><code class="trace-code">{{ dash(scope.row.clientIp) }}</code></template>
      </el-table-column>
      <el-table-column label="客户端 UA" min-width="220" show-overflow-tooltip>
        <template #default="scope"><code class="trace-code">{{ dash(scope.row.userAgent) }}</code></template>
      </el-table-column>
      <el-table-column label="结果" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">{{ scope.row.result }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-footer">
      <span>共 {{ total }} 条记录</span>
      <el-pagination
        v-model:current-page="page"
        layout="prev, pager, next"
        :page-size="size"
        :total="total"
        @current-change="load"
      />
    </div>
  </section>
</template>
