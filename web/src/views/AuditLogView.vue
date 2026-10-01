<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { request, type OperationLog, type Page } from '../api/http'
const loading = ref(false); const action = ref(''); const data = ref<Page<OperationLog>>({ items: [], total: 0, page: 1, pageSize: 20 })
async function load() { loading.value = true; try { data.value = await request<Page<OperationLog>>({ url: '/operation-logs', params: { page: 1, pageSize: 20, action: action.value || undefined } }) } catch (e) { ElMessage.error(e instanceof Error ? e.message : '日志加载失败') } finally { loading.value = false } }
onMounted(load)
</script>

<template><div class="page-head"><div><span class="section-kicker">AUDIT / TRACEABLE ACTIONS</span><h1>审计日志</h1><p>记录业务动作、对象、结果和 traceId，不保存密码、原图或完整 OCR JSON。</p></div><el-button class="quiet-button" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></div><section class="surface-panel table-panel"><div class="filter-row"><el-input v-model="action" placeholder="按动作筛选，如 OCR_TRIGGER" class="search-input" @keyup.enter="load"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-button type="primary" @click="load">查询</el-button><span class="filter-total">共 {{ data.total }} 条</span></div><el-table v-loading="loading" :data="data.items" class="data-table" empty-text="暂无审计记录"><el-table-column prop="createdAt" label="时间" width="190" /><el-table-column prop="action" label="动作" width="180"><template #default="scope"><code class="action-code">{{ scope.row.action }}</code></template></el-table-column><el-table-column prop="objectType" label="对象" width="160" /><el-table-column prop="objectId" label="对象 ID" width="100" /><el-table-column prop="result" label="结果" width="100"><template #default="scope"><el-tag :type="scope.row.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">{{ scope.row.result }}</el-tag></template></el-table-column><el-table-column prop="traceId" label="traceId" min-width="260"><template #default="scope"><code class="trace-code">{{ scope.row.traceId }}</code></template></el-table-column></el-table></section></template>
