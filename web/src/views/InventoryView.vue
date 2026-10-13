<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Close, DocumentChecked, Plus, Refresh, View, Warning } from '@element-plus/icons-vue'
import {
  request,
  type ArchiveDocument,
  type ArchiveVersion,
  type Employee,
  type InventoryItem,
  type InventoryTask,
  type InventoryVersionOption,
  type Page,
} from '../api/http'
import { maxLength, positiveValue, requiredText } from '../utils/live-validation'

const page = ref(1)
const pageSize = 10
const loading = ref(false)
const itemLoading = ref(false)
const versionLoading = ref(false)
const employeeLoading = ref(false)
const documentLoading = ref(false)
const createVersionLoading = ref(false)
const saving = ref(false)
const tasks = ref<Page<InventoryTask>>({ items: [], total: 0, page: 1, pageSize })
const selectedTask = ref<InventoryTask | null>(null)
const items = ref<InventoryItem[]>([])
const versionOptions = ref<InventoryVersionOption[]>([])
const showCreate = ref(false)
const showCheck = ref(false)
const showResolve = ref(false)
const activeItem = ref<InventoryItem | null>(null)
const employeeOptions = ref<Employee[]>([])
const documentOptions = ref<ArchiveDocument[]>([])
const createVersionOptions = ref<ArchiveVersion[]>([])
const selectedEmployeeId = ref<number>()
const selectedDocumentId = ref<number>()
const selectedCreateVersionId = ref<number>()
const selectedVersions = ref<Array<{
  versionId: number
  employeeNo: string
  employeeName: string
  documentTitle: string
  versionNo: number
  isCurrent: boolean
}>>([])
const checkForm = reactive({ actualStatus: 'PRESENT', actualVersionId: 0, note: '' })
const scopeForm = computed(() => ({ employeeId: selectedEmployeeId.value, documentId: selectedDocumentId.value, versionId: selectedCreateVersionId.value }))
const resolutionForm = reactive({ resolution: '' })
let employeeSearchSequence = 0
let documentLoadSequence = 0
let createVersionLoadSequence = 0

const taskStatusLabel: Record<string, string> = {
  DRAFT: '草稿', IN_PROGRESS: '进行中', ABNORMAL: '存在异常', COMPLETED: '已完成',
}
const actualStatusLabel: Record<string, string> = {
  PRESENT: '在库', MISSING: '缺失', DAMAGED: '损坏',
}
const differenceLabel: Record<string, string> = {
  NONE: '无差异', MISSING: '材料缺失', DAMAGED: '材料损坏', VERSION_MISMATCH: '版本不符',
}
const resolutionStatusLabel: Record<string, string> = {
  PENDING: '待盘点', NOT_REQUIRED: '无差异', OPEN: '待处理', RESOLVED: '已处理',
}
const checkTitle = computed(() => {
  const expected = versionOptions.value.find((version) => version.id === activeItem.value?.expectedVersionId)
  return expected ? `登记 V${expected.versionNo} 盘点实况` : '登记盘点实况'
})

async function load() {
  loading.value = true
  try {
    tasks.value = await request<Page<InventoryTask>>({
      url: '/inventory-tasks', params: { page: page.value, pageSize },
    })
    if (selectedTask.value) {
      selectedTask.value = tasks.value.items.find((task) => task.id === selectedTask.value?.id) || null
      if (!selectedTask.value) items.value = []
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '盘点任务加载失败')
  } finally {
    loading.value = false
  }
}

async function loadItems(taskId: number) {
  itemLoading.value = true
  try {
    items.value = await request<InventoryItem[]>({ url: `/inventory-tasks/${taskId}/items` })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '盘点明细加载失败')
  } finally {
    itemLoading.value = false
  }
}

async function selectTask(task: InventoryTask) {
  selectedTask.value = task
  await loadItems(task.id)
}

function resetCreateForm() {
  employeeSearchSequence += 1
  documentLoadSequence += 1
  createVersionLoadSequence += 1
  employeeOptions.value = []
  documentOptions.value = []
  createVersionOptions.value = []
  employeeLoading.value = false
  documentLoading.value = false
  createVersionLoading.value = false
  selectedEmployeeId.value = undefined
  selectedDocumentId.value = undefined
  selectedCreateVersionId.value = undefined
  selectedVersions.value = []
}

function openCreate() {
  resetCreateForm()
  showCreate.value = true
}

async function searchEmployees(keyword: string) {
  const sequence = ++employeeSearchSequence
  const query = keyword.trim()
  if (!query) {
    employeeOptions.value = employeeOptions.value.filter((employee) => employee.id === selectedEmployeeId.value)
    employeeLoading.value = false
    return
  }

  employeeLoading.value = true
  try {
    const result = await request<Page<Employee>>({
      url: '/employees', params: { keyword: query, page: 1, pageSize: 20 },
    })
    if (sequence === employeeSearchSequence) {
      const selected = employeeOptions.value.find((employee) => employee.id === selectedEmployeeId.value)
      employeeOptions.value = selected && !result.items.some((employee) => employee.id === selected.id)
        ? [...result.items, selected] : result.items
    }
  } catch (error) {
    if (sequence === employeeSearchSequence) {
      ElMessage.error(error instanceof Error ? error.message : '员工搜索失败')
    }
  } finally {
    if (sequence === employeeSearchSequence) employeeLoading.value = false
  }
}

async function loadDocuments(employeeId?: number) {
  const sequence = ++documentLoadSequence
  selectedDocumentId.value = undefined
  documentOptions.value = []
  createVersionOptions.value = []
  selectedCreateVersionId.value = undefined
  if (!employeeId) {
    documentLoading.value = false
    return
  }

  documentLoading.value = true
  try {
    const result = await request<Page<ArchiveDocument>>({
      url: `/archive/employees/${employeeId}/documents`, params: { page: 1, pageSize: 100 },
    })
    if (sequence === documentLoadSequence) documentOptions.value = result.items
  } catch (error) {
    if (sequence === documentLoadSequence) {
      ElMessage.error(error instanceof Error ? error.message : '员工档案材料加载失败')
    }
  } finally {
    if (sequence === documentLoadSequence) documentLoading.value = false
  }
}

async function loadCreateVersions(documentId?: number) {
  const sequence = ++createVersionLoadSequence
  createVersionOptions.value = []
  selectedCreateVersionId.value = undefined
  if (!documentId) {
    createVersionLoading.value = false
    return
  }

  createVersionLoading.value = true
  try {
    const result = await request<Page<ArchiveVersion>>({
      url: `/archive/documents/${documentId}/versions`, params: { page: 1, pageSize: 100 },
    })
    if (sequence === createVersionLoadSequence) createVersionOptions.value = result.items
  } catch (error) {
    if (sequence === createVersionLoadSequence) {
      ElMessage.error(error instanceof Error ? error.message : '档案版本加载失败')
    }
  } finally {
    if (sequence === createVersionLoadSequence) createVersionLoading.value = false
  }
}

function addSelectedVersion() {
  const employee = employeeOptions.value.find((option) => option.id === selectedEmployeeId.value)
  const document = documentOptions.value.find((option) => option.id === selectedDocumentId.value)
  const version = createVersionOptions.value.find((option) => option.id === selectedCreateVersionId.value)
  if (!employee || !document || !version) return
  if (selectedVersions.value.some((item) => item.versionId === version.id)) {
    return ElMessage.warning('该档案版本已在盘点范围中')
  }
  selectedVersions.value.push({
    versionId: version.id,
    employeeNo: employee.employeeNo,
    employeeName: employee.name,
    documentTitle: document.title,
    versionNo: version.versionNo,
    isCurrent: version.isCurrent,
  })
  selectedCreateVersionId.value = undefined
}

async function createTask() {
  const versionIds = [...new Set(selectedVersions.value.map((item) => item.versionId))]
  if (!versionIds.length) return ElMessage.warning('请至少加入一个档案版本')
  saving.value = true
  try {
    const created = await request<InventoryTask>({
      url: '/inventory-tasks', method: 'POST', data: { versionIds },
    })
    showCreate.value = false
    resetCreateForm()
    ElMessage.success('盘点任务已创建')
    await load()
    const current = tasks.value.items.find((task) => task.id === created.id) || created
    await selectTask(current)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '盘点任务创建失败')
  } finally {
    saving.value = false
  }
}

async function startTask(task: InventoryTask) {
  saving.value = true
  try {
    const updated = await request<InventoryTask>({
      url: `/inventory-tasks/${task.id}/start`, method: 'POST',
    })
    ElMessage.success('盘点任务已开始')
    await load()
    if (selectedTask.value?.id === task.id) {
      selectedTask.value = updated
      await loadItems(task.id)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '无法开始盘点')
  } finally {
    saving.value = false
  }
}

async function completeTask(task: InventoryTask) {
  saving.value = true
  try {
    const updated = await request<InventoryTask>({
      url: `/inventory-tasks/${task.id}/complete`, method: 'POST',
    })
    ElMessage.success('盘点任务已完成')
    await load()
    if (selectedTask.value?.id === task.id) {
      selectedTask.value = updated
      await loadItems(task.id)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '盘点尚未完成')
  } finally {
    saving.value = false
  }
}

async function openCheck(item: InventoryItem) {
  activeItem.value = item
  checkForm.actualStatus = 'PRESENT'
  checkForm.actualVersionId = item.expectedVersionId
  checkForm.note = ''
  versionOptions.value = []
  showCheck.value = true
  versionLoading.value = true
  try {
    versionOptions.value = await request<InventoryVersionOption[]>({
      url: `/inventory-tasks/${item.taskId}/items/${item.id}/versions`,
    })
    if (!versionOptions.value.some((version) => version.id === item.expectedVersionId)) {
      checkForm.actualVersionId = versionOptions.value[0]?.id || 0
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '档案版本加载失败')
  } finally {
    versionLoading.value = false
  }
}

async function submitCheck() {
  if (!selectedTask.value || !activeItem.value) return
  if (checkForm.actualStatus !== 'MISSING' && !checkForm.actualVersionId) {
    return ElMessage.warning('请选择现场档案版本')
  }
  saving.value = true
  try {
    await request<InventoryItem>({
      url: `/inventory-tasks/${selectedTask.value.id}/items/${activeItem.value.id}/check`,
      method: 'POST',
      data: {
        actualStatus: checkForm.actualStatus,
        actualVersionId: checkForm.actualStatus === 'MISSING'
          ? undefined : checkForm.actualVersionId || undefined,
        note: checkForm.note,
      },
    })
    showCheck.value = false
    ElMessage.success('盘点实况已登记')
    await loadItems(selectedTask.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '实况登记失败')
  } finally {
    saving.value = false
  }
}

function openResolve(item: InventoryItem) {
  activeItem.value = item
  resolutionForm.resolution = ''
  showResolve.value = true
}

async function submitResolution() {
  if (!selectedTask.value || !activeItem.value) return
  if (!resolutionForm.resolution.trim()) return ElMessage.warning('请填写异常处理结果')
  saving.value = true
  try {
    await request<InventoryItem>({
      url: `/inventory-tasks/${selectedTask.value.id}/items/${activeItem.value.id}/resolve`,
      method: 'POST', data: { resolution: resolutionForm.resolution.trim() },
    })
    showResolve.value = false
    ElMessage.success('异常处理结果已归档')
    await loadItems(selectedTask.value.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '异常处理失败')
  } finally {
    saving.value = false
  }
}

function taskTagType(status: string) {
  return status === 'COMPLETED' ? 'success' : status === 'ABNORMAL' ? 'danger'
    : status === 'IN_PROGRESS' ? 'warning' : 'info'
}

function itemTagType(status: string) {
  return status === 'OPEN' ? 'danger' : status === 'RESOLVED' || status === 'NOT_REQUIRED'
    ? 'success' : 'info'
}

onMounted(load)
</script>

<template>
  <div class="page-head">
    <div>
      <span class="section-kicker">INVENTORY / EXCEPTION CONTROL</span>
      <h1>盘点异常</h1>
    </div>
    <div class="head-actions">
      <el-button class="quiet-button" :loading="loading" title="刷新盘点任务" @click="load">
        <el-icon><Refresh /></el-icon>
      </el-button>
      <el-button type="primary" class="action-button" @click="openCreate">
        <el-icon><Plus /></el-icon>创建盘点任务
      </el-button>
    </div>
  </div>

  <section class="surface-panel table-panel">
    <div class="filter-row">
      <span class="section-kicker">TASK REGISTER</span>
      <span class="filter-total">共 {{ tasks.total }} 项</span>
    </div>
    <el-table v-loading="loading" :data="tasks.items" row-key="id" class="data-table" empty-text="暂无盘点任务">
      <el-table-column prop="taskNo" label="任务编号" min-width="230">
        <template #default="scope"><code class="employee-code">{{ scope.row.taskNo }}</code></template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="130">
        <template #default="scope">
          <el-tag :type="taskTagType(scope.row.status)" effect="plain">
            {{ taskStatusLabel[scope.row.status] || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="scope">
          <el-button link type="primary" title="查看盘点明细" @click="selectTask(scope.row as InventoryTask)">
            <el-icon><View /></el-icon>明细
          </el-button>
          <el-button v-if="scope.row.status === 'DRAFT'" link type="success" :loading="saving" @click="startTask(scope.row as InventoryTask)">
            <el-icon><DocumentChecked /></el-icon>开始
          </el-button>
          <el-button v-if="scope.row.status === 'IN_PROGRESS'" link type="warning" :loading="saving" @click="completeTask(scope.row as InventoryTask)">
            <el-icon><CircleCheck /></el-icon>完成
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-footer">
      <span>第 {{ page }} 页</span>
      <el-pagination v-model:current-page="page" layout="prev, pager, next" :page-size="pageSize"
        :total="tasks.total" @current-change="load" />
    </div>
  </section>

  <section v-if="selectedTask" class="surface-panel table-panel inventory-detail">
    <div class="filter-row inventory-detail-head">
      <div>
        <span class="section-kicker">{{ selectedTask.taskNo }}</span>
        <h2>盘点明细</h2>
      </div>
      <el-tag :type="taskTagType(selectedTask.status)" effect="plain">
        {{ taskStatusLabel[selectedTask.status] || selectedTask.status }}
      </el-tag>
    </div>
    <el-table v-loading="itemLoading" :data="items" row-key="id" class="data-table" empty-text="该任务没有可见盘点项目">
      <el-table-column label="员工" min-width="190">
        <template #default="scope">
          <strong>{{ scope.row.employeeNo || '—' }}</strong>
          <span v-if="scope.row.employeeName"> · {{ scope.row.employeeName }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="documentTitle" label="档案材料" min-width="180" show-overflow-tooltip />
      <el-table-column prop="expectedVersionNo" label="应有版本" width="120">
        <template #default="scope">{{ scope.row.expectedVersionNo ? `V${scope.row.expectedVersionNo}` : '—' }}</template>
      </el-table-column>
      <el-table-column prop="actualStatus" label="现场状态" width="120">
        <template #default="scope">{{ actualStatusLabel[scope.row.actualStatus] || '未登记' }}</template>
      </el-table-column>
      <el-table-column prop="differenceType" label="差异" width="130">
        <template #default="scope">{{ differenceLabel[scope.row.differenceType] || '待核对' }}</template>
      </el-table-column>
      <el-table-column prop="differenceDescription" label="现场记录" min-width="210" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.differenceDescription || '—' }}</template>
      </el-table-column>
      <el-table-column prop="resolutionStatus" label="处理状态" width="130">
        <template #default="scope">
          <el-tag :type="itemTagType(scope.row.resolutionStatus)" effect="plain">
            {{ resolutionStatusLabel[scope.row.resolutionStatus] || scope.row.resolutionStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-button v-if="selectedTask.status === 'IN_PROGRESS' && scope.row.resolutionStatus === 'PENDING'"
            link type="primary" title="登记现场状态" @click="openCheck(scope.row as InventoryItem)">
            <el-icon><DocumentChecked /></el-icon>登记实况
          </el-button>
          <el-button v-else-if="selectedTask.status === 'IN_PROGRESS' && scope.row.resolutionStatus === 'OPEN'"
            link type="danger" title="填写异常处理结果" @click="openResolve(scope.row as InventoryItem)">
            <el-icon><Warning /></el-icon>处理差异
          </el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>
  </section>

  <el-dialog v-model="showCreate" class="inventory-create-dialog" title="创建盘点任务" width="620px" destroy-on-close>
    <div class="inventory-create-form">
      <el-form label-position="top" class="inventory-scope-form" :model="scopeForm">
        <el-form-item label="员工" prop="employeeId" :rules="[positiveValue('请选择员工。')]">
          <el-select v-model="selectedEmployeeId" class="inventory-full-width inventory-employee-select"
            filterable remote clearable :remote-method="searchEmployees" :loading="employeeLoading"
            placeholder="输入员工编号或姓名搜索" no-data-text="未找到当前权限范围内的员工"
            @change="loadDocuments">
            <el-option v-for="employee in employeeOptions" :key="employee.id" :value="employee.id"
              :label="`${employee.employeeNo} · ${employee.name}`">
              <span>{{ employee.employeeNo }} · {{ employee.name }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="档案材料" prop="documentId" :rules="[positiveValue('请选择档案材料。')]">
          <el-select v-model="selectedDocumentId" class="inventory-full-width inventory-document-select"
            filterable clearable :loading="documentLoading" :disabled="documentLoading || !documentOptions.length"
            placeholder="选择档案材料" no-data-text="该员工暂无档案材料" @change="loadCreateVersions">
            <el-option v-for="document in documentOptions" :key="document.id" :value="document.id"
              :label="document.title">
              <span>{{ document.title }}</span><small class="inventory-option-meta">{{ document.documentType }}</small>
            </el-option>
          </el-select>
          <p v-if="selectedEmployeeId && !documentLoading && !documentOptions.length" class="inventory-field-help">
            该员工在当前权限范围内暂无档案材料
          </p>
        </el-form-item>
        <el-form-item label="档案版本" prop="versionId" :rules="[positiveValue('请选择档案版本。')]">
          <div class="inventory-version-picker">
            <el-select v-model="selectedCreateVersionId" class="inventory-full-width inventory-version-select"
              filterable :loading="createVersionLoading"
              :disabled="createVersionLoading || !createVersionOptions.length"
              popper-class="inventory-create-versions-popper"
              placeholder="选择材料版本" no-data-text="该材料暂无可选版本">
              <el-option v-for="version in createVersionOptions" :key="version.id" :value="version.id"
                :disabled="selectedVersions.some((item) => item.versionId === version.id)"
                :label="`V${version.versionNo}${version.isCurrent ? ' · 当前版本' : ' · 历史版本'}`" />
            </el-select>
            <el-button type="primary" :disabled="!selectedCreateVersionId" @click="addSelectedVersion">
              <el-icon><Plus /></el-icon>加入范围
            </el-button>
          </div>
          <p v-if="selectedDocumentId && !createVersionLoading && !createVersionOptions.length" class="inventory-field-help">
            该材料暂无可纳入盘点的档案版本
          </p>
        </el-form-item>
      </el-form>

      <section class="inventory-scope-section" aria-label="本次盘点范围">
        <div class="inventory-scope-heading">
          <strong>本次盘点范围</strong>
          <span>{{ selectedVersions.length }} 份材料</span>
        </div>
        <div v-if="selectedVersions.length" class="inventory-scope-list">
          <div v-for="item in selectedVersions" :key="item.versionId" class="inventory-scope-entry">
            <div class="inventory-scope-entry-copy">
              <strong>{{ item.employeeNo }} · {{ item.employeeName }}</strong>
              <small>{{ item.documentTitle }} · V{{ item.versionNo }} · {{ item.isCurrent ? '当前版本' : '历史版本' }}</small>
            </div>
            <el-button link type="danger" :title="`移除 ${item.documentTitle} V${item.versionNo}`"
              :aria-label="`移除 ${item.documentTitle} V${item.versionNo}`"
              @click="selectedVersions = selectedVersions.filter((entry) => entry.versionId !== item.versionId)">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
        <p v-else class="inventory-scope-empty">选择档案版本并加入盘点范围</p>
      </section>
    </div>
    <template #footer>
      <el-button @click="showCreate = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!selectedVersions.length" @click="createTask">创建</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="showCheck" :title="checkTitle" width="520px" destroy-on-close>
    <el-form label-position="top" :model="checkForm">
      <el-form-item label="现场状态">
        <el-select v-model="checkForm.actualStatus" class="inventory-full-width">
          <el-option label="在库" value="PRESENT" />
          <el-option label="缺失" value="MISSING" />
          <el-option label="损坏" value="DAMAGED" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="checkForm.actualStatus !== 'MISSING'" label="现场档案版本" prop="actualVersionId" :rules="[positiveValue('请选择现场档案版本。')]">
        <el-select v-model="checkForm.actualVersionId" class="inventory-full-width inventory-version-select"
          :loading="versionLoading" :disabled="versionLoading || !versionOptions.length" filterable>
          <el-option v-for="version in versionOptions" :key="version.id" :value="version.id"
            :label="`V${version.versionNo}${version.isCurrent ? ' · 当前版本' : ' · 历史版本'}`" />
        </el-select>
      </el-form-item>
      <el-form-item label="盘点说明" prop="note" :rules="[maxLength(512)]">
        <el-input v-model="checkForm.note" type="textarea" :rows="3" maxlength="512" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showCheck = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitCheck">登记实况</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="showResolve" title="处理盘点差异" width="520px" destroy-on-close>
    <div v-if="activeItem" class="inventory-exception-summary">
      <el-tag type="danger" effect="plain">
        {{ differenceLabel[activeItem.differenceType || ''] || activeItem.differenceType || '异常' }}
      </el-tag>
      <span>{{ activeItem.differenceDescription }}</span>
    </div>
    <el-form label-position="top" :model="resolutionForm">
      <el-form-item label="处理结果" prop="resolution" :rules="[requiredText('请填写异常处理结果。'), maxLength(512)]">
        <el-input v-model="resolutionForm.resolution" type="textarea" :rows="3" maxlength="512" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showResolve = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitResolution">归档处理结果</el-button>
    </template>
  </el-dialog>
</template>
