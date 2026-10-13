<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Check, Clock, Close, Document, Lock, Refresh, Upload, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import {
  http,
  request,
  type AccessApplication,
  type ArchiveAccessDetail,
  type ArchiveVersion,
  type Employee,
  type Page,
} from '../api/http'
import { maxLength, notAfter, notBefore, positiveValue, requiredList, requiredText, requiredValue } from '../utils/live-validation'

const loading = ref(false)
const route = useRoute()
const auth = useAuthStore()
const page = ref(1)
const data = ref<Page<AccessApplication>>({ items: [], total: 0, page: 1, pageSize: 10 })
const filters = reactive({ status: '' })
const showCreate = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const employeeOptions = ref<Employee[]>([])
const archiveOptions = ref<ArchiveVersion[]>([])
const loadingArchives = ref(false)
const createForm = reactive<{ employeeId?: number; purpose: string; useType: string; startAt: string; dueAt: string; versionIds: number[] }>({
  purpose: '', useType: 'READ', startAt: '', dueAt: '', versionIds: [],
})
const showReturn = ref(false)
const returning = ref(false)
const returnFormRef = ref<FormInstance>()
const returnApplication = ref<AccessApplication>()
const returnEmployees = ref<Employee[]>([])
const returnForm = reactive<{ abnormal: boolean; remark: string; handoverToEmployeeId?: number }>({
  abnormal: false, remark: '', handoverToEmployeeId: undefined,
})
const returnRemarkError = computed(() => {
  const required = returnForm.abnormal || returnApplication.value?.status === 'ABNORMAL'
  return required && !returnForm.remark.trim() ? '请填写异常说明。' : ''
})
const showDetail = ref(false)
const detailLoading = ref(false)
const detail = ref<ArchiveAccessDetail>()
const showReject = ref(false)
const rejecting = ref(false)
const rejectApplication = ref<AccessApplication>()
const rejectReason = ref('')

const statusLabel: Record<string, string> = {
  DRAFT: '草稿', PENDING_APPROVAL: '待授权', APPROVED: '已授权', IN_USE: '使用中',
  RETURNED: '已归还', ABNORMAL: '异常', EXPIRED: '已过期', REJECTED: '已驳回',
}
const returnStatusLabel: Record<string, string> = { RETURNED: '已归还', ABNORMAL: '异常', HANDED_OVER: '已移交' }
const useTypeLabel: Record<string, string> = { READ: '查阅', BORROW: '借用', HANDOVER: '移交' }
const canManage = () => auth.roles.includes('HR_ADMIN')
const canOperate = (item: AccessApplication) => item.applicantId === auth.user?.id || canManage()
const canSubmit = (item: AccessApplication) => item.applicantId === auth.user?.id

async function load() {
  loading.value = true
  try {
    data.value = await request<Page<AccessApplication>>({
      url: '/archive-access',
      params: { page: page.value, pageSize: 10, status: filters.status || undefined },
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '授权列表加载失败')
  } finally {
    loading.value = false
  }
}

async function action(item: AccessApplication, name: string) {
  try {
    await request<AccessApplication>({ url: `/archive-access/${item.id}/${name}`, method: 'POST' })
    ElMessage.success('状态已更新')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
  }
}

function openReject(item: AccessApplication) {
  rejectApplication.value = item
  rejectReason.value = ''
  showReject.value = true
}

async function submitReject() {
  const item = rejectApplication.value
  const reason = rejectReason.value.trim()
  if (!item || !reason) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  rejecting.value = true
  try {
    await request<AccessApplication>({
      url: `/archive-access/${item.id}/reject`,
      method: 'POST',
      data: { reason },
    })
    showReject.value = false
    ElMessage.success('申请已驳回')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '驳回申请失败')
  } finally {
    rejecting.value = false
  }
}

async function openCreate(prefill?: { employeeId?: number; versionId?: number }) {
  showCreate.value = true
  createForm.employeeId = undefined
  createForm.versionIds = []
  archiveOptions.value = []
  try {
    employeeOptions.value = (await request<Page<Employee>>({
      url: '/employees', params: { page: 1, pageSize: 100, status: 'ACTIVE' },
    })).items
    if (prefill?.employeeId) {
      let employee = employeeOptions.value.find((item) => item.id === prefill.employeeId)
      if (!employee) {
        employee = await request<Employee>({ url: `/employees/${prefill.employeeId}` })
        employeeOptions.value = [employee, ...employeeOptions.value]
      }
      createForm.employeeId = employee.id
      await loadArchiveVersions(employee.id, prefill.versionId)
      if (prefill.versionId && archiveOptions.value.some((version) => version.id === prefill.versionId)) {
        createForm.versionIds = [prefill.versionId]
      }
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '员工列表加载失败')
  }
}

async function loadArchiveVersions(employeeId?: number, includeVersionId?: number) {
  createForm.versionIds = []
  archiveOptions.value = []
  if (!employeeId) return
  loadingArchives.value = true
  try {
    const documents = await request<Page<{ id: number; title: string }>>({
      url: `/archive/employees/${employeeId}/documents`, params: { page: 1, pageSize: 100 },
    })
    const versions = await Promise.all(documents.items.map((document) => request<Page<ArchiveVersion>>({
      url: `/archive/documents/${document.id}/versions`, params: { page: 1, pageSize: 100 },
    })))
    archiveOptions.value = versions.flatMap((result) => result.items)
      .filter((version) => version.isCurrent || version.id === includeVersionId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '档案版本加载失败')
  } finally {
    loadingArchives.value = false
  }
}

async function createAccess() {
  const valid = await createFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  if (!createForm.employeeId || !createForm.purpose.trim() || !createForm.versionIds.length || !createForm.dueAt) {
    ElMessage.warning('请完整选择员工、档案版本并填写用途和归还时间')
    return
  }
  creating.value = true
  try {
    await request<AccessApplication>({
      url: '/archive-access',
      method: 'POST',
      data: {
        purpose: createForm.purpose.trim(),
        useType: createForm.useType,
        startAt: createForm.startAt || undefined,
        dueAt: createForm.dueAt,
        versionIds: createForm.versionIds,
      },
    })
    showCreate.value = false
    ElMessage.success('授权申请已创建')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建授权申请失败')
  } finally {
    creating.value = false
  }
}

async function openReturn(item: AccessApplication) {
  returnApplication.value = item
  returnForm.abnormal = false
  returnForm.remark = ''
  returnForm.handoverToEmployeeId = undefined
  returnEmployees.value = []
  if (item.useType === 'HANDOVER') {
    try {
      returnEmployees.value = (await request<Page<Employee>>({
        url: '/employees',
        params: { page: 1, pageSize: 100, departmentId: item.departmentId, status: 'ACTIVE' },
      })).items
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '接收员工列表加载失败')
      return
    }
  }
  showReturn.value = true
}

async function submitReturn() {
  const item = returnApplication.value
  if (!item) return
  const valid = await returnFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  if ((returnForm.abnormal || item.status === 'ABNORMAL') && !returnForm.remark.trim()) {
    ElMessage.warning('异常归还必须填写异常说明')
    return
  }
  if (item.useType === 'HANDOVER' && !returnForm.abnormal && !returnForm.handoverToEmployeeId) {
    ElMessage.warning('请选择移交接收员工')
    return
  }
  returning.value = true
  try {
    await request<AccessApplication>({
      url: `/archive-access/${item.id}/return`,
      method: 'POST',
      data: {
        abnormal: returnForm.abnormal,
        remark: returnForm.remark,
        handoverToEmployeeId: returnForm.abnormal ? undefined : returnForm.handoverToEmployeeId,
      },
    })
    showReturn.value = false
    ElMessage.success(returnForm.abnormal ? '异常归还已登记' : '归还或移交已登记')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '归还登记失败')
  } finally {
    returning.value = false
  }
}

async function openDetail(item: AccessApplication) {
  showDetail.value = true
  detailLoading.value = true
  detail.value = undefined
  try {
    detail.value = await request<ArchiveAccessDetail>({ url: `/archive-access/${item.id}` })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '档案授权记录加载失败')
    showDetail.value = false
  } finally {
    detailLoading.value = false
  }
}

function canReadDetailVersion() {
  if (!detail.value) return false
  const application = detail.value.application
  if (application.status === 'PENDING_APPROVAL') return canManage()
  const now = Date.now()
  return application.status === 'IN_USE' && canOperate(application)
    && new Date(application.startAt).getTime() <= now
    && new Date(application.dueAt).getTime() >= now
}

async function downloadVersion(versionId: number) {
  try {
    const response = await http.get(`/archive/versions/${versionId}/download`, { responseType: 'blob' })
    const disposition = String(response.headers['content-disposition'] || '')
    const filename = disposition.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i)
    const name = filename?.[1] ? decodeURIComponent(filename[1]) : filename?.[2] || `archive-version-${versionId}.bin`
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = name
    anchor.click()
    setTimeout(() => URL.revokeObjectURL(url), 0)
  } catch (error) {
    const status = (error as { status?: number })?.status
    ElMessage.error(status === 403
      ? '当前档案授权未生效或已过期，请刷新授权记录。'
      : error instanceof Error ? error.message : '原图查看失败')
  }
}

function formatDate(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN') : '—'
}

function accountLabel(name?: string, id?: number) {
  return name || (id ? `账号 #${id}` : '—')
}

function employeeLabel(name?: string, employeeNo?: string, id?: number) {
  if (name && employeeNo) return `${employeeNo} · ${name}`
  if (name) return name
  if (employeeNo) return employeeNo
  return id ? `员工 #${id}` : '—'
}

onMounted(async () => {
  await load()
  const targetId = Number(route.query.applicationId)
  const target = Number.isInteger(targetId) && targetId > 0 ? data.value.items.find((item) => item.id === targetId) : undefined
  if (target) await openDetail(target)
  else {
    const employeeId = Number(route.query.employeeId)
    const versionId = Number(route.query.versionId)
    if (Number.isInteger(employeeId) && employeeId > 0 && Number.isInteger(versionId) && versionId > 0) {
      await openCreate({ employeeId, versionId })
    }
  }
})
</script>

<template>
  <div class="page-head">
    <div>
      <span class="section-kicker">ACCESS / CHAIN OF CUSTODY</span>
      <h1>档案授权</h1>
    </div>
    <el-button type="primary" class="action-button" @click="openCreate()">
      <el-icon><Lock /></el-icon>新建授权申请
    </el-button>
  </div>

  <section class="surface-panel table-panel">
    <div class="filter-row">
      <el-select v-model="filters.status" clearable placeholder="全部状态" class="compact-select">
        <el-option v-for="(label, value) in statusLabel" :key="value" :label="label" :value="value" />
      </el-select>
      <el-button type="primary" @click="page = 1; load()"><el-icon><Refresh /></el-icon>刷新</el-button>
      <span class="filter-total">共 {{ data.total }} 条</span>
    </div>
    <el-table v-loading="loading" :data="data.items" class="data-table" empty-text="暂无授权申请">
      <el-table-column prop="applicationNo" label="申请编号" min-width="200">
        <template #default="scope"><code class="employee-code">{{ scope.row.applicationNo }}</code></template>
      </el-table-column>
      <el-table-column prop="purpose" label="申请用途" min-width="180" show-overflow-tooltip />
      <el-table-column prop="useType" label="使用方式" width="110">
        <template #default="scope">{{ useTypeLabel[scope.row.useType] || scope.row.useType }}</template>
      </el-table-column>
      <el-table-column prop="dueAt" label="归还截止" width="180">
        <template #default="scope">
          <span :class="new Date(scope.row.dueAt) < new Date() && scope.row.status === 'IN_USE' ? 'text-risk' : ''">
            <el-icon><Clock /></el-icon> {{ formatDate(scope.row.dueAt) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ABNORMAL' || scope.row.status === 'REJECTED' ? 'danger' : scope.row.status === 'IN_USE' ? 'warning' : scope.row.status === 'RETURNED' ? 'success' : 'info'" effect="plain">
            {{ statusLabel[scope.row.status] || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="scope">
          <el-button link title="查看档案使用记录" @click="openDetail(scope.row as AccessApplication)"><el-icon><Document /></el-icon>记录</el-button>
          <el-button v-if="scope.row.status === 'DRAFT' && canSubmit(scope.row as AccessApplication)" link type="primary" title="提交授权申请" @click="action(scope.row as AccessApplication, 'submit')"><el-icon><Upload /></el-icon>提交</el-button>
          <el-button v-if="scope.row.status === 'PENDING_APPROVAL' && canManage()" link type="success" @click="action(scope.row as AccessApplication, 'approve')"><el-icon><Check /></el-icon>授权</el-button>
          <el-button v-if="scope.row.status === 'PENDING_APPROVAL' && canManage()" link type="danger" title="驳回授权申请" @click="openReject(scope.row as AccessApplication)"><el-icon><Close /></el-icon>驳回</el-button>
          <el-button v-if="scope.row.status === 'APPROVED' && canOperate(scope.row as AccessApplication)" link type="primary" @click="action(scope.row as AccessApplication, 'use')"><el-icon><User /></el-icon>开始使用</el-button>
          <el-button v-if="scope.row.status === 'IN_USE' && canOperate(scope.row as AccessApplication)" link type="warning" title="登记归还" @click="openReturn(scope.row as AccessApplication)"><el-icon><Refresh /></el-icon>归还</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-footer">
      <span>第 {{ page }} 页</span>
      <el-pagination v-model:current-page="page" layout="prev, pager, next" :page-size="10" :total="data.total" @current-change="load" />
    </div>
  </section>

  <el-dialog v-model="showCreate" title="新建档案授权申请" width="min(560px, calc(100vw - 32px))">
    <el-form ref="createFormRef" label-position="top" :model="createForm">
      <el-form-item label="员工" prop="employeeId" :rules="[requiredValue('请选择员工。')]">
        <el-select v-model="createForm.employeeId" filterable placeholder="选择档案所属员工" @change="loadArchiveVersions">
          <el-option v-for="employee in employeeOptions" :key="employee.id" :label="`${employee.employeeNo} · ${employee.name}`" :value="employee.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="档案版本" prop="versionIds" :rules="[requiredList('至少选择一个档案版本。')]">
        <el-select v-model="createForm.versionIds" multiple filterable :loading="loadingArchives" :disabled="!createForm.employeeId" placeholder="选择当前有效版本">
          <el-option v-for="version in archiveOptions" :key="version.id" :label="`${version.originalName} · V${version.versionNo}`" :value="version.id" />
        </el-select>
        <small v-if="createForm.employeeId && !loadingArchives && !archiveOptions.length" class="form-hint">该员工暂无可申请的档案版本</small>
      </el-form-item>
      <el-form-item label="申请用途" prop="purpose" :rules="[requiredText('请填写申请用途。'), maxLength(512)]"><el-input v-model="createForm.purpose" maxlength="512" placeholder="例如：年度审计查阅" /></el-form-item>
      <el-form-item label="使用方式">
        <el-segmented v-model="createForm.useType" :options="[{ label: '查阅', value: 'READ' }, { label: '借用', value: 'BORROW' }, { label: '移交', value: 'HANDOVER' }]" />
      </el-form-item>
      <el-form-item label="授权开始时间" prop="startAt" :rules="[notAfter(() => createForm.dueAt, '授权开始时间不能晚于归还截止时间。')]"><el-input v-model="createForm.startAt" type="datetime-local" /></el-form-item>
      <el-form-item label="归还截止时间" prop="dueAt" :rules="[requiredText('请选择归还截止时间。'), notBefore(() => createForm.startAt, '归还截止时间不能早于授权开始时间。')]"><el-input v-model="createForm.dueAt" type="datetime-local" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showCreate = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="createAccess">提交申请</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="showReturn" title="登记归还或移交" width="min(560px, calc(100vw - 32px))">
    <el-form ref="returnFormRef" label-position="top" :model="returnForm">
      <el-form-item v-if="returnApplication?.useType === 'HANDOVER' && !returnForm.abnormal" label="接收员工" prop="handoverToEmployeeId" :rules="[positiveValue('请选择接收员工。')]">
        <el-select v-model="returnForm.handoverToEmployeeId" class="handover-select" filterable placeholder="选择本部门在职员工">
          <el-option v-for="employee in returnEmployees" :key="employee.id" :label="`${employee.employeeNo} · ${employee.name}`" :value="employee.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="returnForm.abnormal || returnApplication?.status === 'ABNORMAL' ? '异常说明' : '归还说明'" prop="remark" :error="returnRemarkError" :rules="returnForm.abnormal || returnApplication?.status === 'ABNORMAL' ? [requiredText('请填写异常说明。'), maxLength(512)] : [maxLength(512)]"><el-input v-model="returnForm.remark" type="textarea" :rows="3" maxlength="512" :placeholder="returnForm.abnormal || returnApplication?.status === 'ABNORMAL' ? '说明缺件、损坏或逾期情况' : '记录归还或移交情况'" /></el-form-item>
      <el-checkbox v-model="returnForm.abnormal">归还时发现缺件、损坏或其他异常</el-checkbox>
    </el-form>
    <template #footer>
      <el-button @click="showReturn = false">取消</el-button>
      <el-button type="primary" :loading="returning" @click="submitReturn">确认登记</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="showReject" title="驳回档案授权申请" width="min(480px, calc(100vw - 32px))">
    <el-input v-model="rejectReason" type="textarea" :rows="4" maxlength="512" show-word-limit
      placeholder="说明不予授权的原因" />
    <template #footer>
      <el-button @click="showReject = false">取消</el-button>
      <el-button type="danger" :loading="rejecting" :disabled="!rejectReason.trim()" @click="submitReject">确认驳回</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="showDetail" title="档案授权与使用记录" width="min(900px, calc(100vw - 32px))">
    <div v-loading="detailLoading" class="access-detail">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请编号">{{ detail.application.applicationNo }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ statusLabel[detail.application.status] || detail.application.status }}</el-descriptions-item>
          <el-descriptions-item label="使用方式">{{ useTypeLabel[detail.application.useType] || detail.application.useType }}</el-descriptions-item>
          <el-descriptions-item label="申请用途">{{ detail.application.purpose }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ accountLabel(detail.applicantName, detail.application.applicantId) }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ detail.departmentName || (detail.application.departmentId ? `部门 #${detail.application.departmentId}` : '—') }}</el-descriptions-item>
          <el-descriptions-item label="授权开始">{{ formatDate(detail.application.startAt) }}</el-descriptions-item>
          <el-descriptions-item label="归还截止">{{ formatDate(detail.application.dueAt) }}</el-descriptions-item>
        </el-descriptions>
        <h3>申请档案版本</h3>
        <el-table :data="detail.items" size="small" empty-text="没有关联档案版本">
          <el-table-column label="档案" min-width="160"><template #default="scope">{{ scope.row.documentTitle || `档案版本 ${scope.row.versionId}` }}</template></el-table-column>
          <el-table-column label="版本" width="90"><template #default="scope">{{ scope.row.versionNo ? `V${scope.row.versionNo}` : `#${scope.row.versionId}` }}</template></el-table-column>
          <el-table-column label="员工" min-width="160"><template #default="scope">{{ employeeLabel(scope.row.employeeName, scope.row.employeeNo, scope.row.employeeId) }}</template></el-table-column>
          <el-table-column label="部门" min-width="130"><template #default="scope">{{ scope.row.departmentName || (scope.row.departmentId ? `部门 #${scope.row.departmentId}` : '—') }}</template></el-table-column>
          <el-table-column prop="status" label="材料状态" width="130" />
          <el-table-column label="操作" width="130"><template #default="scope"><el-button v-if="canReadDetailVersion()" link type="primary" title="查看已授权原图" @click="downloadVersion(scope.row.versionId)"><el-icon><Document /></el-icon>查看原图</el-button></template></el-table-column>
        </el-table>
        <h3>使用与交接时间线</h3>
        <el-table :data="detail.uses" size="small" empty-text="暂无使用记录">
          <el-table-column label="开始时间" min-width="165"><template #default="scope">{{ formatDate(scope.row.usedAt) }}</template></el-table-column>
          <el-table-column label="使用人" min-width="140"><template #default="scope">{{ accountLabel(scope.row.operatorName, scope.row.operatorId) }}</template></el-table-column>
          <el-table-column label="归还时间" min-width="165"><template #default="scope">{{ formatDate(scope.row.returnedAt) }}</template></el-table-column>
          <el-table-column label="登记归还人" min-width="140"><template #default="scope">{{ accountLabel(scope.row.returnedByName, scope.row.returnedByUserId) }}</template></el-table-column>
          <el-table-column label="接收员工" min-width="160"><template #default="scope">{{ employeeLabel(scope.row.handoverToEmployeeName, scope.row.handoverToEmployeeNo, scope.row.handoverToEmployeeId) }}</template></el-table-column>
          <el-table-column label="结果" width="110"><template #default="scope">{{ returnStatusLabel[scope.row.returnStatus || ''] || scope.row.returnStatus || '使用中' }}</template></el-table-column>
          <el-table-column prop="remark" label="说明" min-width="140" show-overflow-tooltip />
        </el-table>
        <h3>授权审批记录</h3>
        <el-table :data="detail.decisions" size="small" empty-text="暂无审批记录">
          <el-table-column label="处理时间" min-width="170"><template #default="scope">{{ formatDate(scope.row.decidedAt) }}</template></el-table-column>
          <el-table-column label="处理人" min-width="140"><template #default="scope">{{ accountLabel(scope.row.decidedByName, scope.row.decidedByUserId) }}</template></el-table-column>
          <el-table-column label="结果" width="110"><template #default="scope">{{ scope.row.decision === 'REJECTED' ? '驳回' : '授权' }}</template></el-table-column>
          <el-table-column prop="decisionNote" label="意见" min-width="180" show-overflow-tooltip />
        </el-table>
        <h3>异常处理记录</h3>
        <el-table :data="detail.resolutions" size="small" empty-text="暂无异常处理记录">
          <el-table-column label="处理时间" min-width="170"><template #default="scope">{{ formatDate(scope.row.resolvedAt) }}</template></el-table-column>
          <el-table-column label="处理人" min-width="140"><template #default="scope">{{ accountLabel(scope.row.resolvedByName, scope.row.resolvedByUserId) }}</template></el-table-column>
          <el-table-column prop="resolutionNote" label="处理说明" min-width="180" show-overflow-tooltip />
        </el-table>
      </template>
    </div>
  </el-dialog>
</template>

<style scoped>
.access-detail h3 { margin: 22px 0 10px; font-size: 15px; font-weight: 650; }
.form-hint { display: block; margin-top: 6px; color: var(--text-muted); }
.handover-select, .el-form-item :deep(.el-select) { width: 100%; }
</style>
