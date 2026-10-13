<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { request, type AuditLogDetail, type OperationLog, type Page } from '../api/http'

const loading = ref(false)
const action = ref('')
const data = ref<Page<OperationLog>>({ items: [], total: 0, page: 1, pageSize: 20 })

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AuditLogDetail | null>(null)

/** 空值统一渲染为 “—”，历史日志的风控字段可能为 NULL。 */
function dash(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

function operatorLabel(name?: string | null, operatorId?: number | null): string {
  if (name) return name
  if (operatorId) return `#${operatorId}`
  return '—'
}

async function load() {
  loading.value = true
  try {
    data.value = await request<Page<OperationLog>>({ url: '/operation-logs', params: { page: 1, pageSize: 20, action: action.value || undefined } })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '日志加载失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(row: OperationLog) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await request<AuditLogDetail>({ url: `/operation-logs/${row.id}` })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '日志详情加载失败')
    drawerVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-head">
    <div>
      <span class="section-kicker">AUDIT / TRACEABLE ACTIONS</span>
      <h1>审计日志</h1>
      <p>记录业务动作、对象、结果和 traceId；列表展示脱敏 IP，点击「详情」查看完整风控指纹与变更前后。</p>
    </div>
    <el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
  </div>

  <section class="surface-panel table-panel">
    <div class="filter-row">
      <el-input v-model="action" placeholder="按动作筛选，如 OCR_TRIGGER" class="search-input" @keyup.enter="load">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="load">查询</el-button>
      <span class="filter-total">共 {{ data.total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="data.items" class="data-table" empty-text="暂无审计记录">
      <el-table-column prop="createdAt" label="时间" width="190" />
      <el-table-column label="操作人" width="120">
        <template #default="scope">{{ operatorLabel(scope.row.operatorName, scope.row.operatorId) }}</template>
      </el-table-column>
      <el-table-column prop="action" label="动作" width="200">
        <template #default="scope"><code class="action-code">{{ scope.row.action }}</code></template>
      </el-table-column>
      <el-table-column prop="objectType" label="对象类型" width="160" />
      <el-table-column prop="result" label="结果" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">{{ scope.row.result }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="IP（脱敏）" width="150">
        <template #default="scope"><code class="trace-code">{{ dash(scope.row.clientIpMasked) }}</code></template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="scope">
          <el-button link type="primary" title="查看详情" @click="openDetail(scope.row as OperationLog)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>

  <el-drawer v-model="drawerVisible" title="审计日志详情" size="560px" destroy-on-close>
    <div v-loading="detailLoading" class="audit-detail">
      <template v-if="detail">
        <section class="audit-section">
          <h3 class="audit-section-title">基本信息</h3>
          <div class="audit-desc">
            <div class="audit-desc-row"><span class="audit-desc-label">时间</span><span>{{ dash(detail.createdAt) }}</span></div>
            <div class="audit-desc-row"><span class="audit-desc-label">操作人</span><span>{{ operatorLabel(detail.operatorName, detail.operatorId) }}</span></div>
            <div class="audit-desc-row"><span class="audit-desc-label">动作</span><code class="action-code">{{ dash(detail.action) }}</code></div>
            <div class="audit-desc-row"><span class="audit-desc-label">对象类型</span><span>{{ dash(detail.objectType) }}</span></div>
            <div class="audit-desc-row"><span class="audit-desc-label">对象 ID</span><span>{{ dash(detail.objectId) }}</span></div>
            <div class="audit-desc-row"><span class="audit-desc-label">结果</span>
              <el-tag :type="detail.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">{{ dash(detail.result) }}</el-tag>
            </div>
            <div class="audit-desc-row"><span class="audit-desc-label">trace_id</span><code class="trace-code">{{ dash(detail.traceId) }}</code></div>
          </div>
        </section>

        <section class="audit-section">
          <h3 class="audit-section-title">风控指纹</h3>
          <div class="audit-desc">
            <div class="audit-desc-row"><span class="audit-desc-label">client_ip</span><code class="trace-code">{{ dash(detail.clientIp) }}</code></div>
            <div class="audit-desc-row"><span class="audit-desc-label">user_agent</span><code class="trace-code">{{ dash(detail.userAgent) }}</code></div>
            <div class="audit-desc-row"><span class="audit-desc-label">request_method</span><code class="trace-code">{{ dash(detail.requestMethod) }}</code></div>
            <div class="audit-desc-row"><span class="audit-desc-label">request_uri</span><code class="trace-code">{{ dash(detail.requestUri) }}</code></div>
            <div class="audit-desc-row"><span class="audit-desc-label">session_id</span><code class="trace-code">{{ dash(detail.sessionId) }}</code></div>
          </div>
        </section>

        <section class="audit-section">
          <h3 class="audit-section-title">变更详情</h3>
          <div class="audit-diff">
            <div class="audit-diff-col">
              <span class="audit-diff-label">变更前 before</span>
              <pre class="audit-diff-body">{{ dash(detail.beforeSummary) }}</pre>
            </div>
            <div class="audit-diff-col">
              <span class="audit-diff-label">变更后 after</span>
              <pre class="audit-diff-body">{{ dash(detail.afterSummary) }}</pre>
            </div>
          </div>
        </section>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.audit-detail { padding: 0 4px; }
.audit-section { margin-bottom: 22px; }
.audit-section-title { margin: 0 0 10px; font-size: 12px; font-weight: 650; letter-spacing: .04em; color: #66777a; }
.audit-desc { display: flex; flex-direction: column; gap: 8px; }
.audit-desc-row { display: flex; align-items: baseline; gap: 12px; font-size: 12px; color: var(--ink-soft, #4e5969); }
.audit-desc-label { flex: 0 0 110px; color: #909399; font-size: 11px; }
.audit-diff { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.audit-diff-col { display: flex; flex-direction: column; gap: 6px; }
.audit-diff-label { font-size: 11px; color: #909399; }
.audit-diff-body { margin: 0; padding: 10px; border: 1px solid var(--line, #e7edeb); border-radius: 6px; background: #fafbfb; font-size: 11px; line-height: 1.5; white-space: pre-wrap; overflow-wrap: anywhere; font-family: inherit; min-height: 44px; }
</style>
