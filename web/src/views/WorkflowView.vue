<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Filter, Refresh } from '@element-plus/icons-vue'
import { request, type HrRequest, type Page } from '../api/http'

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const filters = reactive({ status: '', type: '' })
const data = ref<Page<HrRequest>>({ items: [], total: 0, page: 1, pageSize: 10 })
const showCreate = ref(false)
const creating = ref(false)
const createForm = reactive({ requestType: 'TRANSFER', employeeId: '', targetDepartmentId: '', targetPositionId: '', payloadJson: '{"source":"web"}' })
const typeLabel: Record<string, string> = { ONBOARDING: '入职', TRANSFER: '调动', OFFBOARDING: '离职' }
const statusLabel: Record<string, string> = { DRAFT: '草稿', PENDING_DEPT_APPROVAL: '待部门审批', PENDING_HR_APPROVAL: '待人事复核', APPROVED: '已通过', REJECTED: '已驳回' }

async function load() {
  loading.value = true
  try { data.value = await request<Page<HrRequest>>({ url: '/hr-requests', params: { page: page.value, pageSize: pageSize.value, type: filters.type || undefined, status: filters.status || undefined } }) }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '流程加载失败') }
  finally { loading.value = false }
}
async function decide(item: HrRequest, action: 'approve' | 'reject') {
  const comment = await ElMessageBox.prompt(action === 'approve' ? '填写审批意见（可选）' : '填写驳回原因', action === 'approve' ? '审批通过' : '驳回申请', { inputPlaceholder: '最多 512 字' }).then((r) => r.value).catch(() => null)
  if (comment === null) return
  try { await request<HrRequest>({ url: `/hr-requests/${item.id}/${action}`, method: 'POST', data: { comment } }); ElMessage.success(action === 'approve' ? '已通过申请' : '已驳回申请'); load() }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '操作失败') }
}
async function createRequest() {
  if (!createForm.employeeId) return ElMessage.warning('请输入员工 ID')
  creating.value = true
  try {
    await request<HrRequest>({ url: '/hr-requests', method: 'POST', data: { requestType: createForm.requestType, employeeId: Number(createForm.employeeId), targetDepartmentId: createForm.targetDepartmentId ? Number(createForm.targetDepartmentId) : undefined, targetPositionId: createForm.targetPositionId ? Number(createForm.targetPositionId) : undefined, payloadJson: createForm.payloadJson } })
    showCreate.value = false; ElMessage.success('草稿已创建'); load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '创建申请失败') }
  finally { creating.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-head"><div><span class="section-kicker">WORKFLOW / APPROVAL</span><h1>人事流程</h1><p>入职、调动、离职申请均经过部门负责人和人事复核两个节点。</p></div><div class="head-actions"><el-button class="quiet-button" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button><el-button type="primary" class="action-button" @click="showCreate = true">新建申请</el-button></div></div>
  <section class="surface-panel table-panel"><div class="filter-row"><el-select v-model="filters.type" clearable placeholder="流程类型" class="compact-select"><el-option v-for="(label, value) in typeLabel" :key="value" :label="label" :value="value" /></el-select><el-select v-model="filters.status" clearable placeholder="流程状态" class="compact-select"><el-option v-for="(label, value) in statusLabel" :key="value" :label="label" :value="value" /></el-select><el-button type="primary" @click="page = 1; load()"><el-icon><Filter /></el-icon>筛选</el-button><span class="filter-total">共 {{ data.total }} 条</span></div><el-table v-loading="loading" :data="data.items" class="data-table" empty-text="当前没有流程记录"><el-table-column prop="requestNo" label="申请编号" min-width="210"><template #default="scope"><code class="employee-code">{{ scope.row.requestNo }}</code></template></el-table-column><el-table-column prop="requestType" label="类型" width="100"><template #default="scope">{{ typeLabel[scope.row.requestType] || scope.row.requestType }}</template></el-table-column><el-table-column prop="employeeId" label="员工" width="100"><template #default="scope">员工 {{ scope.row.employeeId }}</template></el-table-column><el-table-column prop="status" label="状态" width="150"><template #default="scope"><el-tag :type="scope.row.status.includes('PENDING') ? 'warning' : scope.row.status === 'APPROVED' ? 'success' : scope.row.status === 'REJECTED' ? 'danger' : 'info'" effect="plain">{{ statusLabel[scope.row.status] || scope.row.status }}</el-tag></template></el-table-column><el-table-column prop="createdAt" label="创建时间" width="180" /><el-table-column label="操作" width="180" fixed="right"><template #default="scope"><div v-if="scope.row.status === 'PENDING_DEPT_APPROVAL' || scope.row.status === 'PENDING_HR_APPROVAL'" class="row-actions"><el-button link type="success" @click="decide(scope.row, 'approve')"><el-icon><Check /></el-icon>通过</el-button><el-button link type="danger" @click="decide(scope.row, 'reject')"><el-icon><Close /></el-icon>驳回</el-button></div><span v-else class="muted">无待办操作</span></template></el-table-column></el-table><div class="table-footer"><span>第 {{ page }} 页</span><el-pagination v-model:current-page="page" layout="prev, pager, next" :page-size="pageSize" :total="data.total" @current-change="load" /></div></section>
  <el-dialog v-model="showCreate" title="新建人事申请" width="460px"><el-form label-position="top"><el-form-item label="流程类型"><el-select v-model="createForm.requestType"><el-option v-for="(label, value) in typeLabel" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item label="员工 ID"><el-input v-model="createForm.employeeId" placeholder="请输入员工 ID" /></el-form-item><el-form-item label="目标部门 ID"><el-input v-model="createForm.targetDepartmentId" placeholder="调动或入职时填写" /></el-form-item><el-form-item label="目标岗位 ID"><el-input v-model="createForm.targetPositionId" placeholder="调动或入职时填写" /></el-form-item><el-form-item label="申请说明"><el-input v-model="createForm.payloadJson" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="showCreate = false">取消</el-button><el-button type="primary" :loading="creating" @click="createRequest">创建草稿</el-button></template></el-dialog>
</template>
