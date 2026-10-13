<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Close, Refresh, Upload, User } from '@element-plus/icons-vue'
import { request, type AccessApplication, type ArchiveAccessDetail, type Employee, type HrRequest, type HttpError, type Page, type RequestDetail } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { maxLength, positiveValue, requiredText } from '../utils/live-validation'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const requestDetail = ref<RequestDetail | null>(null)
const accessDetail = ref<ArchiveAccessDetail | null>(null)
const returnVisible = ref(false)
const returning = ref(false)
const rejectionVisible = ref(false)
const rejectionReason = ref('')
const rejectingAccess = ref(false)
const decisionPending = ref(false)
const returnEmployees = ref<Employee[]>([])
const returnForm = reactive({ abnormal: false, remark: '', handoverToEmployeeId: undefined as number | undefined })
const returnRemarkError = computed(() => {
  const required = returnForm.abnormal || accessDetail.value?.application.status === 'ABNORMAL'
  return required && !returnForm.remark.trim() ? '请填写异常说明。' : ''
})

const kind = computed(() => String(route.params.kind || ''))
const resourceId = computed(() => Number(route.params.id))
const isHr = computed(() => kind.value === 'hr-request')
const isArchive = computed(() => kind.value === 'archive-access' || kind.value === 'archive-return')
const title = computed(() => isHr.value ? '人事审批待办' : kind.value === 'archive-return' ? '档案归还待办' : '档案授权待办')
const canManageAccess = computed(() => auth.roles.includes('HR_ADMIN'))
const canSubmitAccess = computed(() => accessDetail.value?.application.applicantId === auth.user?.id)
const canOperateAccess = computed(() => {
  const application = accessDetail.value?.application
  return !!application && (application.applicantId === auth.user?.id || canManageAccess.value)
})
const hasActiveUse = computed(() => accessDetail.value?.uses.some((use) => !use.returnedAt) ?? false)

const typeLabel: Record<string, string> = { ONBOARDING: '入职', TRANSFER: '转岗调动', LEAVE: '请假异动', OFFBOARDING: '离职交接' }
const statusLabel: Record<string, string> = {
  DRAFT: '草稿', PENDING_DEPT_APPROVAL: '待部门审批', PENDING_HR_APPROVAL: '待人事复核', APPROVED: '已授权',
  PENDING_APPROVAL: '待授权', IN_USE: '使用中', RETURNED: '已归还', ABNORMAL: '异常', EXPIRED: '已过期', REJECTED: '已驳回', CANCELLED: '已取消',
}
const decisionLabel: Record<string, string> = { APPROVED: '通过', REJECTED: '驳回' }
const useTypeLabel: Record<string, string> = { READ: '查阅', BORROW: '借阅', HANDOVER: '移交' }
const useResultLabel: Record<string, string> = { RETURNED: '已归还', ABNORMAL: '异常归还', HANDED_OVER: '已移交' }

async function load() {
  loading.value = true
  requestDetail.value = null
  accessDetail.value = null
  try {
    if (isHr.value) {
      requestDetail.value = await request<RequestDetail>({ url: `/hr-requests/${resourceId.value}` })
    } else if (isArchive.value) {
      accessDetail.value = await request<ArchiveAccessDetail>({ url: `/archive-access/${resourceId.value}` })
    } else {
      throw new Error('未知的待办类型')
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '待办详情加载失败')
  } finally {
    loading.value = false
  }
}

function canApprove(item: HrRequest) {
  if (item.status === 'PENDING_DEPT_APPROVAL') return auth.roles.includes('DEPT_MANAGER') && item.approvalDepartmentId === auth.user?.departmentId
  if (item.status === 'PENDING_HR_APPROVAL') return auth.roles.includes('HR_ADMIN')
  return false
}

function canSubmitRequest(item: HrRequest) {
  return item.applicantId === auth.user?.id || auth.roles.includes('HR_ADMIN')
}

async function cancelHrRequest() {
  const item = requestDetail.value?.request
  if (!item || !canSubmitRequest(item)) return
  try {
    await ElMessageBox.confirm('取消后该草稿将保留记录但不能继续提交，确认取消吗？', '取消草稿', { type: 'warning' })
    await request<HrRequest>({ url: `/hr-requests/${item.id}/cancel`, method: 'POST' })
    ElMessage.success('草稿已取消')
    await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '取消失败') }
}

async function decide(action: 'approve' | 'reject') {
  const item = requestDetail.value?.request
  if (!item || decisionPending.value) return
  const comment = await ElMessageBox.prompt(action === 'approve' ? '填写审批意见（可选）' : '填写驳回原因', action === 'approve' ? '审批通过' : '驳回申请', {
    inputPlaceholder: '最多 512 字', inputPattern: /^.{0,512}$/, inputErrorMessage: '审批意见不能超过 512 个字符。',
    inputValidator: (value) => action !== 'reject' || Boolean(value.trim()) || '请填写驳回原因。',
  }).then((result) => result.value).catch(() => null)
  if (comment === null) return
  decisionPending.value = true
  try {
    await request<HrRequest>({ url: `/hr-requests/${item.id}/${action}`, method: 'POST', data: { comment } })
    ElMessage.success(action === 'approve' ? '已通过申请' : '已驳回申请')
    await load()
  } catch (e) {
    if (!(e as HttpError).handled) ElMessage.error(e instanceof Error ? e.message : '审批操作失败')
  } finally {
    decisionPending.value = false
  }
}

async function submitHr() {
  const item = requestDetail.value?.request
  if (!item) return
  try {
    await request<HrRequest>({ url: `/hr-requests/${item.id}/submit`, method: 'POST' })
    ElMessage.success('申请已提交审批')
    await load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '提交失败') }
}

async function completeHandover() {
  const item = requestDetail.value?.request
  if (!item) return
  try {
    await ElMessageBox.confirm('确认已完成离职交接并核对交接人？', '确认交接', { type: 'warning' })
    await request<HrRequest>({ url: `/hr-requests/${item.id}/handover`, method: 'PUT' })
    ElMessage.success('交接已确认')
    await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '交接确认失败') }
}

async function accessAction(action: 'submit' | 'approve' | 'use') {
  const item = accessDetail.value?.application
  if (!item) return
  try {
    await request<AccessApplication>({ url: `/archive-access/${item.id}/${action}`, method: 'POST' })
    ElMessage.success(action === 'approve' ? '授权已通过' : action === 'use' ? '已开始使用' : '申请已提交')
    await load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '档案授权操作失败') }
}

function openRejectAccess() {
  rejectionReason.value = ''
  rejectionVisible.value = true
}

async function submitRejectAccess() {
  const item = accessDetail.value?.application
  const reason = rejectionReason.value.trim()
  if (!item || !reason) return ElMessage.warning('请填写驳回原因')
  rejectingAccess.value = true
  try {
    await request<AccessApplication>({
      url: `/archive-access/${item.id}/reject`,
      method: 'POST',
      data: { reason },
    })
    rejectionVisible.value = false
    ElMessage.success('申请已驳回')
    await load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '驳回申请失败') }
  finally { rejectingAccess.value = false }
}

async function openReturn() {
  const item = accessDetail.value?.application
  if (!item) return
  returnForm.abnormal = false
  returnForm.remark = ''
  returnForm.handoverToEmployeeId = undefined
  returnEmployees.value = []
  if (item.useType === 'HANDOVER') {
    returnEmployees.value = (await request<Page<Employee>>({ url: '/employees', params: { page: 1, pageSize: 100, departmentId: item.departmentId, status: 'ACTIVE' } })).items
  }
  returnVisible.value = true
}

async function submitReturn() {
  const item = accessDetail.value?.application
  if (!item) return
  if ((returnForm.abnormal || item.status === 'ABNORMAL') && !returnForm.remark.trim()) return ElMessage.warning('异常归还必须填写异常说明')
  if (item.useType === 'HANDOVER' && !returnForm.abnormal && !returnForm.handoverToEmployeeId) return ElMessage.warning('请选择移交接收员工')
  returning.value = true
  try {
    await request<AccessApplication>({ url: `/archive-access/${item.id}/return`, method: 'POST', data: { abnormal: returnForm.abnormal, remark: returnForm.remark.trim(), handoverToEmployeeId: returnForm.abnormal ? undefined : returnForm.handoverToEmployeeId } })
    returnVisible.value = false
    ElMessage.success(returnForm.abnormal ? '异常归还已登记' : '归还或移交已登记')
    await load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '归还登记失败') }
  finally { returning.value = false }
}

async function resolveAbnormal() {
  const item = accessDetail.value?.application
  if (!item) return
  const remark = await ElMessageBox.prompt('填写异常处理说明', '关闭档案访问异常', {
    inputPlaceholder: '例如：已补齐材料并完成复核', inputPattern: /^.{1,512}$/, inputErrorMessage: '请输入 1-512 个字符的处理说明。',
  }).then((result) => result.value).catch(() => null)
  if (remark === null) return
  if (!remark.trim()) return ElMessage.warning('异常处理说明不能为空')
  try {
    await request<AccessApplication>({ url: `/archive-access/${item.id}/resolve`, method: 'POST', data: { remark } })
    ElMessage.success('异常已关闭')
    await load()
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '异常关闭失败') }
}

function formatDate(value?: string | null) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
function employeeLabel(name?: string, employeeNo?: string, id?: number) {
  if (name && employeeNo) return `${employeeNo} · ${name}`
  if (name) return name
  if (employeeNo) return employeeNo
  return id ? `员工 #${id}` : '—'
}
function accountLabel(name?: string, id?: number) { return name || (id ? `账号 #${id}` : '—') }
function departmentLabel(name?: string, id?: number) { return name || (id ? `部门 #${id}` : '—') }
function formatRequest(item: HrRequest) {
  const display = requestDetail.value?.display
  const targetDepartment = departmentLabel(display?.targetDepartmentName, item.targetDepartmentId)
  const targetPosition = display?.targetPositionName || (item.targetPositionId ? `岗位 #${item.targetPositionId}` : '—')
  if (item.requestType === 'LEAVE') return `${item.leaveType || '请假'} · ${item.leaveStartDate || '—'} 至 ${item.leaveEndDate || '—'} · ${item.leaveReason || '—'}`
  if (item.requestType === 'OFFBOARDING') return `离职 ${item.leaveDate || '—'} · 交接人 ${item.handoverPerson || '—'} · ${item.reason || '—'}`
  if (item.requestType === 'ONBOARDING') return `入职 ${item.effectiveDate || '—'} · 目标部门 ${targetDepartment} · 目标岗位 ${targetPosition} · 合同 ${item.contractType || '—'}`
  return `生效 ${item.effectiveDate || '—'} · 目标部门 ${targetDepartment} · 目标岗位 ${targetPosition}${item.reason ? ` · ${item.reason}` : ''}`
}

onMounted(load)
</script>

<template>
  <div class="page-head compact-head">
    <div><el-button text @click="router.push('/todos')"><el-icon><ArrowLeft /></el-icon>返回待办</el-button><span class="section-kicker">ACTION CENTER / DETAIL</span><h1>{{ title }}</h1><p>显示业务上下文、状态时间线和当前角色可执行动作。</p></div>
    <el-button class="quiet-button" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
  </div>

  <div v-loading="loading" class="todo-detail-layout">
    <template v-if="requestDetail">
      <section class="surface-panel detail-card">
        <div class="panel-heading"><div><span class="section-kicker">REQUEST / {{ requestDetail.request.requestNo }}</span><h2>{{ typeLabel[requestDetail.request.requestType] || requestDetail.request.requestType }}</h2></div><el-tag :type="requestDetail.request.status.includes('PENDING') ? 'warning' : requestDetail.request.status === 'APPROVED' ? 'success' : requestDetail.request.status === 'REJECTED' ? 'danger' : 'info'" effect="plain">{{ statusLabel[requestDetail.request.status] || requestDetail.request.status }}</el-tag></div>
        <el-descriptions :column="2" border><el-descriptions-item label="申请编号">{{ requestDetail.request.requestNo }}</el-descriptions-item><el-descriptions-item label="员工">{{ employeeLabel(requestDetail.display?.employeeName, requestDetail.display?.employeeNo, requestDetail.request.employeeId) }}</el-descriptions-item><el-descriptions-item label="员工当前部门">{{ departmentLabel(requestDetail.display?.employeeDepartmentName, requestDetail.display?.employeeDepartmentId) }}</el-descriptions-item><el-descriptions-item label="员工当前岗位">{{ requestDetail.display?.employeePositionName || (requestDetail.display?.employeePositionId ? `岗位 #${requestDetail.display.employeePositionId}` : '—') }}</el-descriptions-item><el-descriptions-item label="申请人">{{ accountLabel(requestDetail.display?.applicantName, requestDetail.request.applicantId) }}</el-descriptions-item><el-descriptions-item label="审批部门">{{ departmentLabel(requestDetail.display?.approvalDepartmentName, requestDetail.request.approvalDepartmentId) }}</el-descriptions-item><el-descriptions-item label="申请内容" :span="2">{{ formatRequest(requestDetail.request) }}</el-descriptions-item><el-descriptions-item label="申请时间">{{ formatDate(requestDetail.request.createdAt) }}</el-descriptions-item><el-descriptions-item label="提交时间">{{ formatDate(requestDetail.request.submittedAt) }}</el-descriptions-item><el-descriptions-item label="完成时间">{{ formatDate(requestDetail.request.completedAt) }}</el-descriptions-item><el-descriptions-item label="员工变更执行">{{ formatDate(requestDetail.request.employeeChangeAppliedAt) }}</el-descriptions-item></el-descriptions>
        <div class="detail-actions"><template v-if="requestDetail.request.status === 'DRAFT' && canSubmitRequest(requestDetail.request)"><el-button type="primary" @click="submitHr"><el-icon><Upload /></el-icon>提交审批</el-button><el-button type="warning" plain @click="cancelHrRequest"><el-icon><Close /></el-icon>取消草稿</el-button></template><template v-if="canApprove(requestDetail.request)"><el-button v-if="!(requestDetail.request.requestType === 'OFFBOARDING' && requestDetail.request.status === 'PENDING_HR_APPROVAL')" type="success" :loading="decisionPending" :disabled="decisionPending" @click="decide('approve')"><el-icon><Check /></el-icon>通过</el-button><el-button type="danger" plain :loading="decisionPending" :disabled="decisionPending" @click="decide('reject')"><el-icon><Close /></el-icon>驳回</el-button></template><el-button v-if="requestDetail.request.requestType === 'OFFBOARDING' && requestDetail.request.status === 'PENDING_HR_APPROVAL' && auth.roles.includes('HR_ADMIN')" type="warning" @click="completeHandover"><el-icon><Check /></el-icon>确认交接</el-button></div>
      </section>
      <section class="surface-panel detail-card"><div class="panel-heading"><div><span class="section-kicker">APPROVAL TIMELINE</span><h2>审批时间线</h2></div></div><el-timeline v-if="requestDetail.approvals.length"><el-timeline-item v-for="approval in requestDetail.approvals" :key="approval.id" :timestamp="formatDate(approval.decidedAt)" :type="approval.decision === 'APPROVED' ? 'success' : 'danger'"><strong>{{ approval.nodeCode === 'DEPARTMENT' ? '部门审批' : 'HR 复核' }} · {{ decisionLabel[approval.decision] || approval.decision }}</strong><p>{{ accountLabel(approval.approverName, approval.approverId) }}{{ approval.comment ? ` · ${approval.comment}` : '' }}</p></el-timeline-item></el-timeline><el-empty v-else description="尚无审批记录" :image-size="70" /></section>
    </template>

    <template v-else-if="accessDetail">
      <section class="surface-panel detail-card">
        <div class="panel-heading">
          <div><span class="section-kicker">ARCHIVE ACCESS / {{ accessDetail.application.applicationNo }}</span><h2>档案授权与归还</h2></div>
          <el-tag :type="accessDetail.application.status === 'ABNORMAL' || accessDetail.application.status === 'REJECTED' ? 'danger' : accessDetail.application.status === 'RETURNED' ? 'success' : accessDetail.application.status === 'EXPIRED' ? 'info' : 'warning'" effect="plain">{{ statusLabel[accessDetail.application.status] || accessDetail.application.status }}</el-tag>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请编号">{{ accessDetail.application.applicationNo }}</el-descriptions-item>
          <el-descriptions-item label="使用方式">{{ useTypeLabel[accessDetail.application.useType] || accessDetail.application.useType }}</el-descriptions-item>
          <el-descriptions-item label="申请用途" :span="2">{{ accessDetail.application.purpose }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ accountLabel(accessDetail.applicantName, accessDetail.application.applicantId) }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ departmentLabel(accessDetail.departmentName, accessDetail.application.departmentId) }}</el-descriptions-item>
          <el-descriptions-item label="授权开始">{{ formatDate(accessDetail.application.startAt) }}</el-descriptions-item>
          <el-descriptions-item label="归还截止">{{ formatDate(accessDetail.application.dueAt) }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="accessDetail.application.status === 'ABNORMAL' && !hasActiveUse" type="warning" :closable="false" show-icon title="异常归还已登记，待人事关闭异常" class="detail-alert" />
        <el-alert v-if="accessDetail.application.status === 'EXPIRED'" type="info" :closable="false" show-icon title="授权期限内未开始使用，申请已过期，请重新提交授权申请" class="detail-alert" />
        <div class="detail-actions">
          <el-button v-if="accessDetail.application.status === 'DRAFT' && canSubmitAccess" type="primary" @click="accessAction('submit')"><el-icon><Upload /></el-icon>提交申请</el-button>
          <el-button v-if="accessDetail.application.status === 'PENDING_APPROVAL' && canManageAccess" type="success" @click="accessAction('approve')"><el-icon><Check /></el-icon>授权</el-button>
          <el-button v-if="accessDetail.application.status === 'PENDING_APPROVAL' && canManageAccess" type="danger" plain @click="openRejectAccess"><el-icon><Close /></el-icon>驳回</el-button>
          <el-button v-if="accessDetail.application.status === 'APPROVED' && canOperateAccess" type="primary" @click="accessAction('use')"><el-icon><User /></el-icon>开始使用</el-button>
          <el-button v-if="hasActiveUse && canOperateAccess" type="warning" @click="openReturn"><el-icon><Refresh /></el-icon>{{ accessDetail.application.status === 'ABNORMAL' ? '完成逾期归还' : '登记归还/移交' }}</el-button>
          <el-button v-if="accessDetail.application.status === 'ABNORMAL' && canManageAccess && !hasActiveUse" type="success" plain @click="resolveAbnormal">关闭异常</el-button>
        </div>
      </section>
      <section class="surface-panel detail-card"><div class="panel-heading"><div><span class="section-kicker">ARCHIVE VERSIONS</span><h2>申请档案版本</h2></div></div><el-table :data="accessDetail.items" size="small" empty-text="没有关联档案版本"><el-table-column label="档案"><template #default="scope">{{ scope.row.documentTitle || `档案版本 #${scope.row.versionId}` }}</template></el-table-column><el-table-column label="版本" width="100"><template #default="scope">{{ scope.row.versionNo ? `V${scope.row.versionNo}` : `#${scope.row.versionId}` }}</template></el-table-column><el-table-column label="员工" min-width="170"><template #default="scope">{{ employeeLabel(scope.row.employeeName, scope.row.employeeNo, scope.row.employeeId) }}</template></el-table-column><el-table-column label="部门" min-width="140"><template #default="scope">{{ departmentLabel(scope.row.departmentName, scope.row.departmentId) }}</template></el-table-column><el-table-column prop="status" label="材料状态" width="120" /></el-table></section>
      <section class="surface-panel detail-card"><div class="panel-heading"><div><span class="section-kicker">CUSTODY TIMELINE</span><h2>使用、归还与交接</h2></div></div><el-table :data="accessDetail.uses" size="small" empty-text="暂无使用记录"><el-table-column label="使用人" min-width="140"><template #default="scope">{{ accountLabel(scope.row.operatorName, scope.row.operatorId) }}</template></el-table-column><el-table-column label="开始时间" min-width="170"><template #default="scope">{{ formatDate(scope.row.usedAt) }}</template></el-table-column><el-table-column label="归还时间" min-width="170"><template #default="scope">{{ formatDate(scope.row.returnedAt) }}</template></el-table-column><el-table-column label="登记归还人" min-width="140"><template #default="scope">{{ accountLabel(scope.row.returnedByName, scope.row.returnedByUserId) }}</template></el-table-column><el-table-column label="接收员工" min-width="170"><template #default="scope">{{ employeeLabel(scope.row.handoverToEmployeeName, scope.row.handoverToEmployeeNo, scope.row.handoverToEmployeeId) }}</template></el-table-column><el-table-column prop="returnStatus" label="结果" width="120"><template #default="scope">{{ useResultLabel[scope.row.returnStatus] || scope.row.returnStatus || '使用中' }}</template></el-table-column><el-table-column prop="remark" label="说明" show-overflow-tooltip min-width="180" /></el-table></section>
      <section class="surface-panel detail-card"><div class="panel-heading"><div><span class="section-kicker">ACCESS DECISIONS</span><h2>授权审批记录</h2></div></div><el-table :data="accessDetail.decisions" size="small" empty-text="暂无审批记录"><el-table-column label="处理时间" min-width="170"><template #default="scope">{{ formatDate(scope.row.decidedAt) }}</template></el-table-column><el-table-column label="处理人" min-width="140"><template #default="scope">{{ accountLabel(scope.row.decidedByName, scope.row.decidedByUserId) }}</template></el-table-column><el-table-column label="结果" width="100"><template #default="scope">{{ decisionLabel[scope.row.decision] || scope.row.decision }}</template></el-table-column><el-table-column prop="decisionNote" label="意见" show-overflow-tooltip min-width="200" /></el-table></section>
      <section class="surface-panel detail-card"><div class="panel-heading"><div><span class="section-kicker">EXCEPTION RESOLUTION</span><h2>异常处理记录</h2></div></div><el-table :data="accessDetail.resolutions" size="small" empty-text="暂无异常处理记录"><el-table-column label="处理时间" min-width="170"><template #default="scope">{{ formatDate(scope.row.resolvedAt) }}</template></el-table-column><el-table-column label="处理人" min-width="140"><template #default="scope">{{ accountLabel(scope.row.resolvedByName, scope.row.resolvedByUserId) }}</template></el-table-column><el-table-column prop="resolutionNote" label="处理说明" show-overflow-tooltip min-width="220" /></el-table></section>
    </template>
  </div>

  <el-dialog v-model="returnVisible" title="登记归还或移交" width="min(560px, calc(100vw - 32px))"><el-form label-position="top" :model="returnForm"><el-form-item v-if="accessDetail?.application.useType === 'HANDOVER' && !returnForm.abnormal" label="接收员工" prop="handoverToEmployeeId" :rules="[positiveValue('请选择接收员工。')]"><el-select v-model="returnForm.handoverToEmployeeId" filterable placeholder="选择本部门在职员工"><el-option v-for="person in returnEmployees" :key="person.id" :label="`${person.employeeNo} · ${person.name}`" :value="person.id" /></el-select></el-form-item><el-form-item :label="returnForm.abnormal || accessDetail?.application.status === 'ABNORMAL' ? '异常说明' : '归还说明'" prop="remark" :error="returnRemarkError" :rules="returnForm.abnormal || accessDetail?.application.status === 'ABNORMAL' ? [requiredText('请填写异常说明。'), maxLength(512)] : [maxLength(512)]"><el-input v-model="returnForm.remark" type="textarea" :rows="3" maxlength="512" :placeholder="returnForm.abnormal || accessDetail?.application.status === 'ABNORMAL' ? '说明缺件、损坏或逾期情况' : '记录归还或移交情况'" /></el-form-item><el-checkbox v-model="returnForm.abnormal">发现缺件、损坏或其他异常</el-checkbox></el-form><template #footer><el-button @click="returnVisible = false">取消</el-button><el-button type="primary" :loading="returning" @click="submitReturn">确认登记</el-button></template></el-dialog>
  <el-dialog v-model="rejectionVisible" title="驳回档案授权申请" width="min(480px, calc(100vw - 32px))"><el-input v-model="rejectionReason" type="textarea" :rows="4" maxlength="512" show-word-limit placeholder="说明不予授权的原因" /><template #footer><el-button @click="rejectionVisible = false">取消</el-button><el-button type="danger" :loading="rejectingAccess" :disabled="!rejectionReason.trim()" @click="submitRejectAccess">确认驳回</el-button></template></el-dialog>
</template>

<style scoped>
.todo-detail-layout { display: grid; gap: 14px; }
.detail-card { padding: 20px; }
.detail-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
.detail-alert { margin-top: 16px; }
.detail-card :deep(.el-timeline) { margin: 8px 0 0; }
.detail-card :deep(.el-timeline-item p) { margin: 6px 0 0; color: var(--text-muted); }
.detail-card :deep(.el-select) { width: 100%; }
</style>
