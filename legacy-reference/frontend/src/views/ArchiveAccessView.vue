<template>
  <div>
    <div class="page-heading">
      <div><p class="eyebrow">Archive / Authorization</p><h1>档案授权</h1><p class="heading-copy">申请访问或借用档案，按授权范围记录领取、归还与异常。</p></div>
      <label class="inline-load"><span>加载申请</span><input v-model.number="lookupId" type="number" min="1" placeholder="申请 ID" /><el-button text :icon="Search" :disabled="!lookupId" aria-label="加载档案授权申请" title="加载档案授权申请" @click="loadApplication" /></label>
    </div>
    <div class="workflow-layout">
      <section class="paper-panel form-panel">
        <div class="panel-heading"><div><p class="eyebrow">Access request</p><h2>新建授权申请</h2></div><span class="panel-index">ACCESS</span></div>
        <form class="operation-form" @submit.prevent="createApplication">
          <div class="form-grid two-columns"><label class="form-field"><span>员工 ID</span><input v-model.number="form.employeeId" type="number" min="1" required placeholder="档案所属员工" /></label><label class="form-field"><span>使用方式</span><select v-model="form.useType"><option value="DIGITAL_ACCESS">数字访问</option><option value="PAPER_BORROW">纸质借用</option></select></label></div>
          <div class="form-grid two-columns"><label class="form-field"><span>档案材料 ID</span><input v-model.number="form.archiveDocumentId" type="number" min="1" required placeholder="例如 2001" /></label><label class="form-field"><span>版本 ID（可选）</span><input v-model.number="form.archiveVersionId" type="number" min="1" placeholder="指定版本" /></label></div>
          <div class="form-grid two-columns"><label class="form-field"><span>开始时间</span><input v-model="form.startAt" type="datetime-local" required /></label><label class="form-field"><span>归还时间</span><input v-model="form.dueAt" type="datetime-local" required /></label></div>
          <label class="form-field"><span>使用目的</span><textarea v-model="form.purpose" rows="4" maxlength="512" required placeholder="说明访问或借用的业务目的"></textarea></label>
          <label class="check-field"><input v-model="form.returnRequired" type="checkbox" /><span>需要归还或移交</span></label>
          <div v-if="error" class="inline-error">{{ error }}</div>
          <div class="form-actions"><el-button type="primary" native-type="submit" :loading="working" :disabled="!form.employeeId || !form.archiveDocumentId" :icon="Plus">创建授权申请</el-button><el-button text @click="resetForm">清空</el-button></div>
        </form>
      </section>
      <section class="paper-panel workflow-detail-panel">
        <div class="panel-heading"><div><p class="eyebrow">Authorization state</p><h2>使用状态</h2></div><span v-if="application" class="state-chip" :class="stateClass(application.status)">{{ statusLabel(application.status) }}</span></div>
        <div v-if="loading" class="empty-state">正在加载授权申请</div>
        <div v-else-if="!application" class="workflow-empty"><el-icon><Key /></el-icon><strong>等待一条授权申请</strong><span>创建申请后，在这里继续提交审批、领取和归还。</span></div>
        <div v-else class="workflow-detail">
          <div class="request-id-row"><span>{{ application.applicationNo }}</span><span>申请 ID {{ application.id }}</span></div>
          <dl class="detail-grid"><div><dt>员工 ID</dt><dd>{{ application.employeeId }}</dd></div><div><dt>使用方式</dt><dd>{{ useTypeLabel(application.useType) }}</dd></div><div><dt>材料数量</dt><dd>{{ application.items.length }} 项</dd></div><div><dt>当前节点</dt><dd>{{ nodeLabel(application.currentNode) }}</dd></div></dl>
          <div class="access-window"><span>使用时段</span><strong>{{ formatDate(application.startAt) }} — {{ formatDate(application.dueAt) }}</strong><p>{{ application.purpose }}</p></div>
          <div class="status-track" aria-label="档案授权状态流转"><div v-for="step in accessSteps" :key="step.code" class="status-step" :class="stepState(step.code, application.status)"><span class="status-step-dot"></span><span>{{ step.label }}</span></div></div>
          <div class="detail-actions"><el-button v-if="application.status === 'DRAFT'" type="primary" :icon="Promotion" :loading="working" @click="submitApplication">提交审批</el-button><template v-if="application.status === 'PENDING_DEPT_APPROVAL' || application.status === 'PENDING_HR_APPROVAL'"><el-button type="primary" :icon="Select" :loading="working" @click="approveApplication">通过当前节点</el-button><el-button plain type="danger" :icon="Close" :loading="working" @click="rejectApplication">驳回</el-button></template><el-button v-if="application.status === 'APPROVED'" type="primary" :icon="Unlock" :loading="working" @click="checkout">登记领取</el-button></div>
          <div v-if="application.use" class="use-record"><div class="use-record-heading"><span>当前使用记录</span><span class="state-chip" :class="application.use.status === 'RETURNED' ? 'success' : application.use.status === 'ABNORMAL' ? 'danger' : 'pending'">{{ useStatusLabel(application.use.status) }}</span></div><p>领取时间：{{ formatDate(application.use.checkedOutAt) }} · 应归还：{{ formatDate(application.use.dueAt) }}</p><div v-if="['IN_USE', 'OVERDUE'].includes(application.use.status)" class="return-form"><label class="form-field"><span>归还方式</span><select v-model="returnForm.returnType"><option value="NORMAL">正常归还</option><option value="HANDOVER">移交</option><option value="ABNORMAL">异常归还</option></select></label><label v-if="returnForm.returnType === 'HANDOVER'" class="form-field"><span>移交对象 ID</span><input v-model.number="returnForm.handoverToId" type="number" min="1" /></label><label class="form-field"><span>缺件说明</span><textarea v-model="returnForm.missingDescription" rows="2" placeholder="无异常可留空"></textarea></label><label class="form-field"><span>损坏说明</span><textarea v-model="returnForm.damageDescription" rows="2" placeholder="无异常可留空"></textarea></label><el-button type="primary" :loading="working" :icon="Check" @click="returnUse">登记归还</el-button></div></div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Key, Plus, Promotion, Search, Select, Unlock } from '@element-plus/icons-vue'
import { approveArchiveAccess, checkoutArchiveAccess, createArchiveAccess, getArchiveAccess, rejectArchiveAccess, returnArchiveUse, submitArchiveAccess } from '@/api/workflow'
import type { ArchiveAccessApplication } from '@/types/api'

function dateTime(offsetHours = 0) { const date = new Date(Date.now() + offsetHours * 3600000); const pad = (v: number) => String(v).padStart(2, '0'); return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}` }
const form = reactive({ employeeId: 0, useType: 'DIGITAL_ACCESS', archiveDocumentId: 0, archiveVersionId: 0, startAt: dateTime(), dueAt: dateTime(24), purpose: '', returnRequired: true })
const returnForm = reactive({ returnType: 'NORMAL', handoverToId: 0, missingDescription: '', damageDescription: '' })
const application = ref<ArchiveAccessApplication | null>(null)
const lookupId = ref<number | null>(null)
const working = ref(false); const loading = ref(false); const error = ref('')
const accessSteps = [{ code: 'DRAFT', label: '草稿' }, { code: 'PENDING_DEPT_APPROVAL', label: '部门审批' }, { code: 'PENDING_HR_APPROVAL', label: '人事授权' }, { code: 'APPROVED', label: '已授权' }, { code: 'IN_USE', label: '使用中' }, { code: 'RETURNED', label: '已归还' }]
function createApplication() { return mutate(() => createArchiveAccess({ employeeId: form.employeeId, useType: form.useType, purpose: form.purpose.trim(), startAt: form.startAt, dueAt: form.dueAt, returnRequired: form.returnRequired, items: [{ archiveDocumentId: form.archiveDocumentId, archiveVersionId: form.archiveVersionId || null, scope: form.useType === 'DIGITAL_ACCESS' ? 'READ' : 'BORROW' }] }), '授权申请已创建') }
async function loadApplication() { if (!lookupId.value) return; loading.value = true; error.value = ''; try { application.value = await getArchiveAccess(lookupId.value) } catch (e) { error.value = e instanceof Error ? e.message : '授权申请加载失败'; application.value = null } finally { loading.value = false } }
async function submitApplication() { await mutate(() => submitArchiveAccess(application.value!.id), '授权申请已提交') }
async function approveApplication() { await mutate(() => approveArchiveAccess(application.value!.id, '管理端授权通过'), '授权节点已通过') }
async function rejectApplication() { try { await ElMessageBox.confirm('确认驳回当前授权申请？', '授权确认', { type: 'warning' }); await mutate(() => rejectArchiveAccess(application.value!.id, '管理端驳回'), '授权申请已驳回') } catch { /* 用户取消 */ } }
async function checkout() { await mutate(() => checkoutArchiveAccess(application.value!.id), '已登记领取') }
async function returnUse() { if (!application.value?.use) return; await mutate(() => returnArchiveUse(application.value!.use!.id, { returnType: returnForm.returnType, handoverToId: returnForm.handoverToId || null, missingDescription: returnForm.missingDescription.trim(), damageDescription: returnForm.damageDescription.trim() }), '归还记录已保存') }
async function mutate(action: () => Promise<ArchiveAccessApplication | import('@/types/api').ArchiveUseRecord>, message: string) { working.value = true; error.value = ''; try { const result = await action(); if ('applicationId' in result) { if (application.value?.id) application.value = await getArchiveAccess(application.value.id) } else { application.value = result as ArchiveAccessApplication }; ElMessage.success(message) } catch (e) { error.value = e instanceof Error ? e.message : '操作失败' } finally { working.value = false } }
function resetForm() { Object.assign(form, { employeeId: 0, useType: 'DIGITAL_ACCESS', archiveDocumentId: 0, archiveVersionId: 0, startAt: dateTime(), dueAt: dateTime(24), purpose: '', returnRequired: true }); error.value = '' }
function useTypeLabel(value: string) { return ({ DIGITAL_ACCESS: '数字访问', PAPER_BORROW: '纸质借用' }[value] || value) }
function statusLabel(value: string) { return ({ DRAFT: '草稿', PENDING_DEPT_APPROVAL: '部门审批中', PENDING_HR_APPROVAL: '人事授权中', APPROVED: '已授权', IN_USE: '使用中', RETURNED: '已归还', ABNORMAL: '异常处理', REJECTED: '已驳回' }[value] || value) }
function nodeLabel(value: string) { return ({ DRAFT: '草稿', DEPT_APPROVAL: '部门负责人', HR_APPROVAL: '人事管理员', COMPLETED: '流程结束' }[value] || value) }
function useStatusLabel(value: string) { return statusLabel(value) }
function formatDate(value: string | null) { return value ? value.replace('T', ' ').slice(0, 16) : '—' }
function stateClass(value: string) { return ['REJECTED', 'ABNORMAL'].includes(value) ? 'danger' : ['APPROVED', 'RETURNED'].includes(value) ? 'success' : 'pending' }
function stepState(code: string, status: string) { const order = ['DRAFT', 'PENDING_DEPT_APPROVAL', 'PENDING_HR_APPROVAL', 'APPROVED', 'IN_USE', 'RETURNED']; const actual = order.indexOf(status); return { done: actual >= order.indexOf(code), current: code === status, rejected: status === 'REJECTED' && code === 'PENDING_DEPT_APPROVAL' } }
</script>
