<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Check, Close, Filter, Refresh, Upload } from '@element-plus/icons-vue'
import { request, type DepartmentNode, type Employee, type HrRequest, type HrRequestCreate, type Page, type PositionOption } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { maxLength, notAfter, notBefore, requiredText, requiredValue } from '../utils/live-validation'

const auth = useAuthStore()
const route = useRoute()
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const filters = reactive({ status: '', type: '' })
const data = ref<Page<HrRequest>>({ items: [], total: 0, page: 1, pageSize: 10 })
const employees = ref<Employee[]>([])
const departments = ref<DepartmentNode[]>([])
const positions = ref<PositionOption[]>([])
const highlightedRequestId = ref<number | null>(null)
const showCreate = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
type HrRequestType = HrRequestCreate['requestType']
type LeaveRequestType = Extract<HrRequestCreate, { requestType: 'LEAVE' }>['leaveType']
type OnboardingContractType = Extract<HrRequestCreate, { requestType: 'ONBOARDING' }>['contractType']
const createForm = reactive({
  requestType: 'TRANSFER' as HrRequestType, employeeId: '', targetDepartmentId: '', targetPositionId: '',
  effectiveDate: '', contractType: 'FIXED_TERM' as OnboardingContractType, leaveType: 'ANNUAL' as LeaveRequestType, leaveStartDate: '',
  leaveEndDate: '', leaveDate: '', handoverPerson: '', reason: '',
})
const typeLabel: Record<string, string> = { ONBOARDING: '入职', TRANSFER: '转岗调动', LEAVE: '请假异动', OFFBOARDING: '离职交接' }
const statusLabel: Record<string, string> = { DRAFT: '草稿', PENDING_DEPT_APPROVAL: '待部门审批', PENDING_HR_APPROVAL: '待人事复核', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已撤回' }
const leaveTypeLabel: Record<string, string> = { ANNUAL: '年假', SICK: '病假', PERSONAL: '事假', OTHER: '其他' }
const contractTypeLabel: Record<string, string> = { FIXED_TERM: '固定期限', OPEN_ENDED: '无固定期限', INTERN: '实习' }
const flatDepartments = computed(() => {
  const result: DepartmentNode[] = []
  const visit = (nodes: DepartmentNode[]) => nodes.forEach((node) => { result.push(node); visit(node.children || []) })
  visit(departments.value)
  return result
})

async function load() {
  loading.value = true
  try {
    data.value = await request<Page<HrRequest>>({ url: '/hr-requests', params: { page: page.value, pageSize: pageSize.value, type: filters.type || undefined, status: filters.status || undefined } })
    const targetId = Number(route.query.requestId)
    highlightedRequestId.value = Number.isInteger(targetId) && targetId > 0 && data.value.items.some((item) => item.id === targetId) ? targetId : null
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '流程加载失败') }
  finally { loading.value = false }
}

async function loadLookups() {
  try {
    const [employeePage, tree] = await Promise.all([
      request<Page<Employee>>({ url: '/employees', params: { page: 1, pageSize: 100 } }),
      request<DepartmentNode[]>({ url: '/departments/tree' }),
    ])
    employees.value = employeePage.items
    departments.value = tree
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '基础数据加载失败') }
}

async function loadPositions(departmentId: string) {
  createForm.targetPositionId = ''
  positions.value = departmentId
    ? await request<PositionOption[]>({ url: `/departments/${departmentId}/positions` }).catch(() => [])
    : []
}

function canApprove(item: HrRequest) {
  if (item.status === 'PENDING_DEPT_APPROVAL') {
    return auth.roles.includes('DEPT_MANAGER')
      && item.approvalDepartmentId === auth.user?.departmentId
  }
  if (item.status === 'PENDING_HR_APPROVAL') return auth.roles.includes('HR_ADMIN')
  return false
}

async function decide(item: HrRequest, action: 'approve' | 'reject') {
  const comment = await ElMessageBox.prompt(action === 'approve' ? '填写审批意见（可选）' : '填写驳回原因', action === 'approve' ? '审批通过' : '驳回申请', {
    inputPlaceholder: '最多 512 字', inputPattern: /^.{0,512}$/, inputErrorMessage: '审批意见不能超过 512 个字符。',
    inputValidator: (value) => action !== 'reject' || Boolean(value.trim()) || '请填写驳回原因。',
  }).then((r) => r.value).catch(() => null)
  if (comment === null) return
  try {
    await request<HrRequest>({ url: `/hr-requests/${item.id}/${action}`, method: 'POST', data: { comment } })
    ElMessage.success(action === 'approve' ? '已通过申请' : '已驳回申请')
    load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '操作失败') }
}

async function submitDraft(item: HrRequest) {
  try {
    await request<HrRequest>({ url: `/hr-requests/${item.id}/submit`, method: 'POST' })
    ElMessage.success('申请已提交审批')
    load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '提交失败') }
}

async function cancelDraft(item: HrRequest) {
  try {
    await ElMessageBox.confirm('取消后该草稿将保留记录但不能继续提交，确认取消吗？', '取消草稿', { type: 'warning' })
    await request<HrRequest>({ url: `/hr-requests/${item.id}/cancel`, method: 'POST' })
    ElMessage.success('草稿已取消')
    load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '取消失败') }
}

async function completeHandover(item: HrRequest) {
  try {
    await ElMessageBox.confirm('确认已完成离职交接并核对交接人？', '确认交接', { type: 'warning' })
    await request<HrRequest>({ url: `/hr-requests/${item.id}/handover`, method: 'PUT' })
    ElMessage.success('交接已确认，可以进行人事复核')
    load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '交接确认失败') }
}

function openCreate() {
  createForm.requestType = 'TRANSFER'
  createForm.employeeId = ''
  createForm.targetDepartmentId = ''
  createForm.targetPositionId = ''
  createForm.effectiveDate = ''
  createForm.leaveStartDate = ''
  createForm.leaveEndDate = ''
  createForm.leaveDate = ''
  createForm.handoverPerson = ''
  createForm.reason = ''
  showCreate.value = true
}

function buildRequestPayload(): HrRequestCreate | null {
  if (!createForm.employeeId) {
    ElMessage.warning('请选择员工')
    return null
  }
  const employeeId = Number(createForm.employeeId)
  if (createForm.requestType === 'TRANSFER' || createForm.requestType === 'ONBOARDING') {
    if (!createForm.targetDepartmentId || !createForm.targetPositionId || !createForm.effectiveDate) {
      ElMessage.warning('请填写目标部门、岗位和生效日期')
      return null
    }
    const shared = {
      employeeId,
      targetDepartmentId: Number(createForm.targetDepartmentId),
      targetPositionId: Number(createForm.targetPositionId),
      effectiveDate: createForm.effectiveDate,
    }
    if (createForm.requestType === 'ONBOARDING') {
      return { requestType: 'ONBOARDING', ...shared, contractType: createForm.contractType }
    }
    if (!createForm.reason.trim()) {
      ElMessage.warning('请填写调动原因')
      return null
    }
    return { requestType: 'TRANSFER', ...shared, reason: createForm.reason.trim() }
  }
  if (createForm.requestType === 'LEAVE') {
    if (!createForm.leaveStartDate || !createForm.leaveEndDate || !createForm.reason.trim()) {
      ElMessage.warning('请填写请假日期和原因')
      return null
    }
    return {
      requestType: 'LEAVE', employeeId, leaveType: createForm.leaveType,
      leaveStartDate: createForm.leaveStartDate, leaveEndDate: createForm.leaveEndDate,
      reason: createForm.reason.trim(),
    }
  }
  if (!createForm.leaveDate || !createForm.handoverPerson.trim()) {
    ElMessage.warning('请填写离职日期和交接人')
    return null
  }
  return {
    requestType: 'OFFBOARDING', employeeId, leaveDate: createForm.leaveDate,
    handoverPerson: createForm.handoverPerson.trim(), reason: createForm.reason.trim() || undefined,
  }
}

async function createRequest() {
  const valid = await createFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  const payload = buildRequestPayload()
  if (!payload) return
  creating.value = true
  try {
    await request<HrRequest>({ url: '/hr-requests', method: 'POST', data: payload })
    showCreate.value = false
    ElMessage.success('草稿已创建，可在列表中提交审批')
    load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '创建申请失败') }
  finally { creating.value = false }
}

function employeeLabel(employee: Employee) { return `${employee.employeeNo} · ${employee.name}` }
function requestSummary(item: HrRequest) {
  if (item.requestType === 'ONBOARDING') {
    const department = flatDepartments.value.find((value) => value.id === item.targetDepartmentId)?.name || `部门 ${item.targetDepartmentId}`
    return `入职 ${item.effectiveDate || '未设置'} · ${department} / 岗位 ${item.targetPositionId} · ${contractTypeLabel[item.contractType || ''] || item.contractType || '未设置合同类型'}`
  }
  if (item.requestType === 'TRANSFER') {
    const department = flatDepartments.value.find((value) => value.id === item.targetDepartmentId)?.name || `部门 ${item.targetDepartmentId}`
    return `生效 ${item.effectiveDate || '未设置'} · 调至 ${department} / 岗位 ${item.targetPositionId} · ${item.reason || '无原因说明'}`
  }
  if (item.requestType === 'LEAVE') {
    return `${leaveTypeLabel[item.leaveType || ''] || item.leaveType || '请假'} · ${item.leaveStartDate || '?'} 至 ${item.leaveEndDate || '?'} · ${item.leaveReason || '无原因说明'}`
  }
  return `离职 ${item.leaveDate || '未设置'} · 交接人 ${item.handoverPerson || '未设置'} · ${item.handoverStatus === 'COMPLETED' ? '交接已确认' : '待人事确认'}${item.reason ? ` · ${item.reason}` : ''}`
}
function employeeChangeStatus(item: HrRequest) {
  if (item.status !== 'APPROVED' || item.requestType === 'LEAVE') return ''
  if (item.employeeChangeAppliedAt) return `已执行 · ${item.employeeChangeAppliedAt.replace('T', ' ').slice(0, 16)}`
  const effectiveDate = item.requestType === 'OFFBOARDING' ? item.leaveDate : item.effectiveDate
  return `待生效 · ${effectiveDate || '待定'}`
}
function requestRowClassName({ row }: { row: HrRequest }) {
  return row.id === highlightedRequestId.value ? 'todo-highlight-row' : ''
}
onMounted(() => { load(); loadLookups() })
</script>

<template>
  <div class="page-head">
    <div><span class="section-kicker">WORKFLOW / APPROVAL</span><h1>人事流程</h1><p>入职、调动、请假和离职按部门审批与人事复核留痕。</p></div>
    <div class="head-actions"><el-button class="quiet-button" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button><el-button type="primary" class="action-button" @click="openCreate">新建申请</el-button></div>
  </div>
  <section class="surface-panel table-panel">
    <div class="filter-row"><el-select v-model="filters.type" clearable placeholder="流程类型" class="compact-select"><el-option v-for="(label, value) in typeLabel" :key="value" :label="label" :value="value" /></el-select><el-select v-model="filters.status" clearable placeholder="流程状态" class="compact-select"><el-option v-for="(label, value) in statusLabel" :key="value" :label="label" :value="value" /></el-select><el-button type="primary" @click="page = 1; load()"><el-icon><Filter /></el-icon>筛选</el-button><span class="filter-total">共 {{ data.total }} 条</span></div>
    <el-table v-loading="loading" :data="data.items" :row-class-name="requestRowClassName" class="data-table" empty-text="当前范围没有流程记录">
      <el-table-column prop="requestNo" label="申请编号" min-width="210"><template #default="scope"><code class="employee-code">{{ scope.row.requestNo }}</code></template></el-table-column>
      <el-table-column prop="requestType" label="类型" width="112"><template #default="scope">{{ typeLabel[scope.row.requestType] || scope.row.requestType }}</template></el-table-column>
      <el-table-column prop="employeeId" label="员工" min-width="170"><template #default="scope">{{ employees.find((item) => item.id === scope.row.employeeId)?.name || `员工 ${scope.row.employeeId}` }}</template></el-table-column>
      <el-table-column label="申请内容" min-width="330" show-overflow-tooltip><template #default="scope">{{ requestSummary(scope.row as HrRequest) }}</template></el-table-column>
      <el-table-column prop="status" label="状态" width="150"><template #default="scope"><el-tag :type="scope.row.status.includes('PENDING') ? 'warning' : scope.row.status === 'APPROVED' ? 'success' : scope.row.status === 'REJECTED' ? 'danger' : 'info'" effect="plain">{{ statusLabel[scope.row.status] || scope.row.status }}</el-tag></template></el-table-column>
      <el-table-column label="员工变更" width="190"><template #default="scope"><span v-if="employeeChangeStatus(scope.row as HrRequest)">{{ employeeChangeStatus(scope.row as HrRequest) }}</span><span v-else class="muted">-</span></template></el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="285" fixed="right"><template #default="scope"><div class="row-actions">
        <template v-if="scope.row.status === 'DRAFT' && (scope.row.applicantId === auth.user?.id || auth.roles.includes('HR_ADMIN'))"><el-button link type="primary" @click="submitDraft(scope.row as HrRequest)"><el-icon><Upload /></el-icon>提交</el-button><el-button link type="warning" @click="cancelDraft(scope.row as HrRequest)"><el-icon><Close /></el-icon>取消</el-button></template>
        <template v-if="canApprove(scope.row as HrRequest)"><el-button v-if="!(scope.row.requestType === 'OFFBOARDING' && scope.row.status === 'PENDING_HR_APPROVAL')" link type="success" @click="decide(scope.row as HrRequest, 'approve')"><el-icon><Check /></el-icon>通过</el-button><el-button link type="danger" @click="decide(scope.row as HrRequest, 'reject')"><el-icon><Close /></el-icon>驳回</el-button></template>
        <el-button v-if="scope.row.requestType === 'OFFBOARDING' && scope.row.status === 'PENDING_HR_APPROVAL' && auth.roles.includes('HR_ADMIN')" link type="warning" @click="completeHandover(scope.row as HrRequest)"><el-icon><Check /></el-icon>确认交接</el-button>
        <span v-if="scope.row.status !== 'DRAFT' && !canApprove(scope.row as HrRequest) && !(scope.row.requestType === 'OFFBOARDING' && scope.row.status === 'PENDING_HR_APPROVAL' && auth.roles.includes('HR_ADMIN'))" class="muted">无待办操作</span>
      </div></template></el-table-column>
    </el-table>
    <div class="table-footer"><span>第 {{ page }} 页</span><el-pagination v-model:current-page="page" layout="prev, pager, next" :page-size="pageSize" :total="data.total" @current-change="load" /></div>
  </section>

  <el-dialog v-model="showCreate" title="新建人事申请" width="540px" destroy-on-close>
    <el-form ref="createFormRef" label-position="top" class="dialog-form" :model="createForm">
      <el-form-item label="流程类型"><el-select v-model="createForm.requestType"><el-option v-for="(label, value) in typeLabel" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="员工" prop="employeeId" :rules="[requiredValue('请选择员工。')]"><el-select v-model="createForm.employeeId" filterable placeholder="选择员工"><el-option v-for="employee in employees" :key="employee.id" :label="employeeLabel(employee)" :value="String(employee.id)" /></el-select></el-form-item>
      <template v-if="['TRANSFER', 'ONBOARDING'].includes(createForm.requestType)">
        <el-form-item label="目标部门" prop="targetDepartmentId" :rules="[requiredValue('请选择目标部门。')]"><el-select v-model="createForm.targetDepartmentId" filterable placeholder="选择部门" @change="loadPositions"><el-option v-for="department in flatDepartments" :key="department.id" :label="department.name" :value="String(department.id)" /></el-select></el-form-item>
        <el-form-item label="目标岗位" prop="targetPositionId" :rules="[requiredValue('请选择目标岗位。')]"><el-select v-model="createForm.targetPositionId" :disabled="!createForm.targetDepartmentId" placeholder="选择岗位"><el-option v-for="position in positions" :key="position.id" :label="position.name" :value="String(position.id)" /></el-select></el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate" :rules="[requiredText('请选择生效日期。')]"><el-date-picker v-model="createForm.effectiveDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item v-if="createForm.requestType === 'ONBOARDING'" label="合同类型"><el-select v-model="createForm.contractType"><el-option label="固定期限" value="FIXED_TERM" /><el-option label="无固定期限" value="OPEN_ENDED" /><el-option label="实习" value="INTERN" /></el-select></el-form-item>
      </template>
      <template v-if="createForm.requestType === 'LEAVE'">
        <el-form-item label="请假类型" prop="leaveType" :rules="[requiredValue('请选择请假类型。')]"><el-select v-model="createForm.leaveType"><el-option label="年假" value="ANNUAL" /><el-option label="病假" value="SICK" /><el-option label="事假" value="PERSONAL" /><el-option label="其他" value="OTHER" /></el-select></el-form-item>
        <el-form-item label="开始日期" prop="leaveStartDate" :rules="[requiredText('请选择开始日期。'), notAfter(() => createForm.leaveEndDate, '开始日期不能晚于结束日期。')]"><el-date-picker v-model="createForm.leaveStartDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="结束日期" prop="leaveEndDate" :rules="[requiredText('请选择结束日期。'), notBefore(() => createForm.leaveStartDate, '结束日期不能早于开始日期。')]"><el-date-picker v-model="createForm.leaveEndDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      </template>
      <template v-if="createForm.requestType === 'OFFBOARDING'">
        <el-form-item label="离职日期" prop="leaveDate" :rules="[requiredText('请选择离职日期。')]"><el-date-picker v-model="createForm.leaveDate" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="交接人" prop="handoverPerson" :rules="[requiredText('请输入交接人。'), maxLength(128)]"><el-input v-model="createForm.handoverPerson" maxlength="128" /></el-form-item>
      </template>
      <el-form-item v-if="['LEAVE', 'TRANSFER'].includes(createForm.requestType)" label="原因" prop="reason" :rules="[requiredText('请填写申请原因。'), maxLength(512)]"><el-input v-model="createForm.reason" type="textarea" :rows="3" maxlength="512" show-word-limit /></el-form-item>
      <el-form-item v-else-if="createForm.requestType === 'OFFBOARDING'" label="离职说明" prop="reason" :rules="[maxLength(512)]"><el-input v-model="createForm.reason" type="textarea" :rows="3" maxlength="512" show-word-limit /></el-form-item>
    </el-form>
    <template #footer><el-button @click="showCreate = false">取消</el-button><el-button type="primary" :loading="creating" @click="createRequest">创建草稿</el-button></template>
  </el-dialog>
</template>
