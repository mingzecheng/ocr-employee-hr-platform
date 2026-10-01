<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">People / Workflow</p>
        <h1>人事流程</h1>
        <p class="heading-copy">建立申请、提交审批，并保留每一次状态变化。</p>
      </div>
      <div class="heading-actions">
        <label class="inline-load">
          <span>加载申请</span>
          <input v-model.number="lookupId" type="number" min="1" placeholder="申请 ID" />
          <el-button text :icon="Search" :disabled="!lookupId" aria-label="加载人事申请" title="加载人事申请" @click="loadRequest" />
        </label>
      </div>
    </div>

    <div class="workflow-layout">
      <section class="paper-panel form-panel">
        <div class="panel-heading">
          <div><p class="eyebrow">New request</p><h2>创建人事申请</h2></div>
          <span class="panel-index">01 / 03</span>
        </div>
        <form class="operation-form" @submit.prevent="createRequest">
          <div class="form-grid two-columns">
            <label class="form-field"><span>申请类型</span><select v-model="form.requestType"><option value="ONBOARDING">入职</option><option value="TRANSFER">转岗 / 调动</option><option value="RESIGNATION">离职交接</option></select></label>
            <label class="form-field"><span>员工 ID</span><input v-model.number="form.employeeId" type="number" min="1" required placeholder="例如 1001" /></label>
          </div>
          <div class="form-grid two-columns">
            <label class="form-field"><span>生效日期</span><input v-model="form.effectiveDate" type="date" /></label>
            <label class="form-field"><span>目标部门 ID</span><input v-model.number="form.targetDepartmentId" type="number" min="1" placeholder="调动时填写" /></label>
          </div>
          <label class="form-field"><span>申请原因</span><textarea v-model="form.reason" rows="4" maxlength="1000" placeholder="填写业务原因和需要留痕的背景"></textarea></label>
          <label class="form-field"><span>交接说明</span><textarea v-model="form.handoverNote" rows="3" maxlength="1000" placeholder="离职或调动时填写交接范围"></textarea></label>
          <div v-if="error" class="inline-error">{{ error }}</div>
          <div class="form-actions"><el-button type="primary" native-type="submit" :loading="working" :disabled="!form.employeeId" :icon="Plus">创建申请</el-button><el-button text type="info" @click="resetForm">清空</el-button></div>
        </form>
      </section>

      <section class="paper-panel workflow-detail-panel">
        <div class="panel-heading">
          <div><p class="eyebrow">Request status</p><h2>申请状态</h2></div>
          <span v-if="request" class="state-chip" :class="stateClass(request.status)">{{ statusLabel(request.status) }}</span>
        </div>
        <div v-if="loading" class="empty-state">正在加载申请</div>
        <div v-else-if="!request" class="workflow-empty"><el-icon><DocumentAdd /></el-icon><strong>创建或加载一条申请</strong><span>申请建立后，可从这里提交、审批或查看当前节点。</span></div>
        <div v-else class="workflow-detail">
          <div class="request-id-row"><span>{{ request.requestNo }}</span><span>申请 ID {{ request.id }}</span></div>
          <dl class="detail-grid">
            <div><dt>流程类型</dt><dd>{{ requestTypeLabel(request.requestType) }}</dd></div>
            <div><dt>员工 ID</dt><dd>{{ request.employeeId }}</dd></div>
            <div><dt>当前节点</dt><dd>{{ nodeLabel(request.currentNode) }}</dd></div>
            <div><dt>发起人 ID</dt><dd>{{ request.applicantId }}</dd></div>
          </dl>
          <div class="payload-summary">
            <span>申请内容</span>
            <p v-if="request.payload.reason">{{ request.payload.reason }}</p>
            <p v-else class="muted-copy">未填写补充说明</p>
          </div>
          <div class="status-track" aria-label="人事申请状态流转">
            <div v-for="step in workflowSteps" :key="step.code" class="status-step" :class="stepState(step.code, request.status)"><span class="status-step-dot"></span><span>{{ step.label }}</span></div>
          </div>
          <div class="detail-actions">
            <el-button v-if="request.status === 'DRAFT'" type="primary" :icon="Promotion" :loading="working" @click="submitRequest">提交审批</el-button>
            <template v-if="request.status === 'PENDING_DEPT_APPROVAL' || request.status === 'PENDING_HR_APPROVAL'">
              <el-button type="primary" :icon="Select" :loading="working" @click="approveRequest">通过当前节点</el-button>
              <el-button plain type="danger" :icon="Close" :loading="working" @click="rejectRequest">驳回申请</el-button>
            </template>
            <span v-if="request.status === 'APPROVED'" class="success-note"><el-icon><CircleCheck /></el-icon>审批已完成，可进入后续业务处理</span>
          </div>
        </div>
      </section>
    </div>

    <section class="paper-panel workflow-guide">
      <div class="panel-heading"><div><p class="eyebrow">Fixed approval chain</p><h2>固定审批链</h2></div><span class="toolbar-note">提交 → 部门负责人 → 人事复核</span></div>
      <div class="guide-steps"><div v-for="(step, index) in ['填写申请', '部门负责人审批', '人事复核', '形成归档记录']" :key="step" class="guide-step"><span>{{ String(index + 1).padStart(2, '0') }}</span><strong>{{ step }}</strong></div></div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Close, DocumentAdd, Plus, Promotion, Search, Select } from '@element-plus/icons-vue'
import { approveHrRequest, createHrRequest, getHrRequest, rejectHrRequest, submitHrRequest } from '@/api/workflow'
import type { HrRequest } from '@/types/api'

const form = reactive({ requestType: 'ONBOARDING', employeeId: 0, effectiveDate: '', targetDepartmentId: 0, reason: '', handoverNote: '' })
const request = ref<HrRequest | null>(null)
const lookupId = ref<number | null>(null)
const working = ref(false)
const loading = ref(false)
const error = ref('')
const workflowSteps = [
  { code: 'DRAFT', label: '草稿' },
  { code: 'PENDING_DEPT_APPROVAL', label: '部门审批' },
  { code: 'PENDING_HR_APPROVAL', label: '人事复核' },
  { code: 'APPROVED', label: '已通过' },
]

function payload() {
  return { effectiveDate: form.effectiveDate || null, targetDepartmentId: form.targetDepartmentId || null, reason: form.reason.trim(), handoverNote: form.handoverNote.trim() }
}
async function createRequest() {
  working.value = true; error.value = ''
  try { request.value = await createHrRequest({ requestType: form.requestType, employeeId: form.employeeId, payload: payload() }); ElMessage.success('人事申请已创建') } catch (e) { error.value = e instanceof Error ? e.message : '申请创建失败' } finally { working.value = false }
}
async function loadRequest() {
  if (!lookupId.value) return
  loading.value = true; error.value = ''
  try { request.value = await getHrRequest(lookupId.value) } catch (e) { error.value = e instanceof Error ? e.message : '申请加载失败'; request.value = null } finally { loading.value = false }
}
async function submitRequest() { await mutate(() => submitHrRequest(request.value!.id), '申请已提交审批') }
async function approveRequest() { await mutate(() => approveHrRequest(request.value!.id, '管理端审批通过'), '当前审批节点已通过') }
async function rejectRequest() {
  try { await ElMessageBox.confirm('确认驳回当前申请？', '审批确认', { type: 'warning' }); await mutate(() => rejectHrRequest(request.value!.id, '管理端审批驳回'), '申请已驳回') } catch { /* 用户取消 */ }
}
async function mutate(action: () => Promise<HrRequest>, message: string) { working.value = true; error.value = ''; try { request.value = await action(); ElMessage.success(message) } catch (e) { error.value = e instanceof Error ? e.message : '状态更新失败' } finally { working.value = false } }
function resetForm() { Object.assign(form, { requestType: 'ONBOARDING', employeeId: 0, effectiveDate: '', targetDepartmentId: 0, reason: '', handoverNote: '' }); error.value = '' }
function requestTypeLabel(type: string) { return ({ ONBOARDING: '入职', TRANSFER: '转岗 / 调动', RESIGNATION: '离职交接' }[type] || type) }
function statusLabel(status: string) { return ({ DRAFT: '草稿', PENDING_DEPT_APPROVAL: '部门审批中', PENDING_HR_APPROVAL: '人事复核中', APPROVED: '已通过', REJECTED: '已驳回' }[status] || status) }
function nodeLabel(node: string) { return ({ DRAFT: '草稿', DEPT_APPROVAL: '部门负责人', HR_APPROVAL: '人事管理员', COMPLETED: '流程结束' }[node] || node) }
function stateClass(status: string) { return status === 'REJECTED' ? 'danger' : status === 'APPROVED' ? 'success' : 'pending' }
function stepState(code: string, status: string) { const order = ['DRAFT', 'PENDING_DEPT_APPROVAL', 'PENDING_HR_APPROVAL', 'APPROVED']; const actual = status === 'REJECTED' ? -1 : order.indexOf(status); return { done: actual >= order.indexOf(code), current: code === status, rejected: status === 'REJECTED' && code === 'PENDING_DEPT_APPROVAL' } }
</script>
