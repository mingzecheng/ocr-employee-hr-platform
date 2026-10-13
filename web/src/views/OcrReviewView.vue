<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, EditPen, Picture, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { http, request, type Employee, type HttpError, type OcrConfirmResult, type OcrResult } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { ocrFieldError } from '../utils/live-validation'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const previewLoading = ref(false)
const result = ref<OcrResult | null>(null)
const previewUrl = ref('')
const previewError = ref('')
const editing = ref('')
const editValue = ref('')
const editReason = ref('')
const editValueError = computed(() => editing.value ? ocrFieldError(editing.value, editValue.value) : '')
const canRevise = auth.roles.includes('HR_ADMIN')
const currentEmployee = ref<Employee | null>(null)
const confirming = ref(false)
const rejecting = ref(false)

/** 后端确认流程的字段白名单，与 OcrConfirmService.CONFIRMABLE_FIELDS 保持一致。 */
const CONFIRMABLE_FIELDS = ['employee_name', 'employee_id', 'phone', 'id_number']
const FIELD_LABELS: Record<string, string> = { employee_name: '姓名', employee_id: '工号', phone: '电话', id_number: '身份证号' }

const revisedFieldCodes = computed(() => new Set((result.value?.revisions || []).map((revision) => revision.fieldCode)))
const blockingFields = computed(() => (result.value?.fields || [])
  .filter((field) => CONFIRMABLE_FIELDS.includes(field.fieldCode)
    && field.confidence < 0.8
    && !revisedFieldCodes.value.has(field.fieldCode)))
const hasBlockingLowConfidence = computed(() => blockingFields.value.length > 0)
const alreadyResolved = computed(() => result.value?.confirmStatus === 'CONFIRMED' || result.value?.confirmStatus === 'REJECTED')
const showConfirmBar = computed(() => canRevise && Boolean(result.value?.bindingId)
  && result.value?.status !== 'FAILED' && result.value?.status !== 'UNAVAILABLE')

function revokePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

async function loadPreview(bindingId?: number) {
  revokePreview()
  previewError.value = ''
  if (!bindingId) return
  previewLoading.value = true
  try {
    const response = await http.get(`/archive/ocr-bindings/${bindingId}/preview`, { responseType: 'blob' })
    previewUrl.value = URL.createObjectURL(response.data)
  } catch (e) {
    previewError.value = e instanceof Error ? e.message : '检测框预览暂时不可用'
  } finally {
    previewLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    result.value = await request<OcrResult>({ url: `/archive/versions/${route.params.versionId}/ocr-result` })
    await loadPreview(result.value.bindingId)
    await loadCurrentEmployee()
  } catch (e) {
    result.value = null
    ElMessage.error(e instanceof Error ? e.message : 'OCR 结果加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCurrentEmployee() {
  const employeeId = Number(route.query.employeeId)
  if (!employeeId) return
  try {
    currentEmployee.value = await request<Employee>({ url: `/employees/${employeeId}` })
  } catch {
    currentEmployee.value = null
  }
}

async function retry() {
  if (!result.value) return
  loading.value = true
  try {
    result.value = await request<OcrResult>({
      url: `/archive/versions/${result.value.versionId}/ocr`,
      method: 'POST',
    })
    await loadPreview(result.value.bindingId)
    ElMessage.success(result.value.status === 'SUCCEEDED' ? 'OCR 已重新识别' : 'OCR 重试仍未完成，请查看失败原因')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : 'OCR 重试失败')
  } finally {
    loading.value = false
  }
}

function beginEdit(field: OcrResult['fields'][number]) {
  editing.value = field.fieldCode
  editValue.value = field.value || ''
  editReason.value = ''
}

async function save(fieldCode: string) {
  if (!result.value?.bindingId) return ElMessage.error('OCR 绑定不存在，无法修订')
  if (editValueError.value) return ElMessage.warning(editValueError.value)
  if (editReason.value.length > 512) return ElMessage.warning('修订原因不能超过 512 个字符。')
  try {
    result.value = await request<OcrResult>({
      url: `/archive/ocr-bindings/${result.value.bindingId}/fields/${encodeURIComponent(fieldCode)}`,
      method: 'PUT',
      data: { value: editValue.value.trim(), reason: editReason.value.trim() || undefined },
    })
    editing.value = ''
    ElMessage.success('字段修订已记录')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '字段修订失败')
  }
}

async function openOriginal() {
  if (!result.value?.versionId) return
  try {
    const response = await http.get(`/archive/versions/${result.value.versionId}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    window.open(url, '_blank', 'noopener,noreferrer')
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '原图打开失败')
  }
}

function masterValue(fieldCode: string): string {
  if (fieldCode === 'employee_name') return currentEmployee.value?.name || '（未设置）'
  if (fieldCode === 'phone') return currentEmployee.value?.phone || '（未设置）'
  return '（仅审计，不回写）'
}

function buildComparison(): string {
  const fields = result.value?.fields || []
  return CONFIRMABLE_FIELDS.map((code) => {
    const field = fields.find((item) => item.fieldCode === code)
    const ocrValue = field?.value || '（未识别）'
    return `${FIELD_LABELS[code] || code}：主档「${masterValue(code)}」 → OCR「${ocrValue}」`
  }).join('\n')
}

function handleConfirmError(e: unknown, fallback: string) {
  const error = e as HttpError
  if (error.handled) return
  if (error.status === 403) return ElMessage.warning('无权限操作')
  ElMessage.error(e instanceof Error ? e.message : fallback)
}

async function confirmBackfill() {
  if (!result.value?.bindingId || confirming.value || rejecting.value) return
  try {
    await ElMessageBox.confirm(buildComparison(), '确认回填员工档案？', {
      type: 'warning',
      confirmButtonText: '确认回填',
      cancelButtonText: '再检查一下',
    })
  } catch {
    return
  }
  confirming.value = true
  try {
    const res = await request<OcrConfirmResult>({
      url: `/archive/ocr-bindings/${result.value.bindingId}/confirm`,
      method: 'POST',
      data: {},
    })
    result.value = { ...result.value, confirmStatus: res.confirmStatus }
    ElMessage.success(res.employeeUpdated ? '已确认并回填员工档案' : '已确认（员工档案无变更）')
  } catch (e) {
    handleConfirmError(e, '确认失败')
  } finally {
    confirming.value = false
  }
}

async function rejectBinding() {
  if (!result.value?.bindingId || confirming.value || rejecting.value) return
  try {
    await ElMessageBox.confirm('标记为无效后，该 OCR 结果将不用于回填员工档案，确认吗？', '标记为无效', {
      type: 'warning',
      confirmButtonText: '标记无效',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  rejecting.value = true
  try {
    const res = await request<OcrConfirmResult>({
      url: `/archive/ocr-bindings/${result.value.bindingId}/reject`,
      method: 'POST',
      data: {},
    })
    result.value = { ...result.value, confirmStatus: res.confirmStatus }
    ElMessage.success('已标记为无效')
  } catch (e) {
    handleConfirmError(e, '操作失败')
  } finally {
    rejecting.value = false
  }
}

onMounted(load)
onBeforeUnmount(revokePreview)
</script>

<template>
  <div class="page-head compact-head">
    <div>
      <el-button text @click="router.back"><el-icon><ArrowLeft /></el-icon>返回</el-button>
      <span class="section-kicker">OCR REVIEW / HUMAN IN THE LOOP</span>
      <h1>识别结果复核</h1>
      <p>原图、检测框、字段校验和人工修订记录保持同一档案版本关联。</p>
    </div>
    <div v-if="result" class="head-actions">
      <el-tag :type="result.status === 'FAILED' || result.status === 'UNAVAILABLE' || result.reviewRequired ? 'warning' : 'success'" size="large" effect="plain">
        <el-icon><WarningFilled v-if="result.status === 'FAILED' || result.status === 'UNAVAILABLE' || result.reviewRequired" /><Check v-else /></el-icon>
        {{ result.status === 'FAILED' ? '识别失败' : result.status === 'UNAVAILABLE' ? '结果暂不可用' : result.reviewRequired ? '需要人工复核' : '已通过校验' }}
      </el-tag>
      <el-button v-if="result.status === 'FAILED' && canRevise" type="primary" :loading="loading" @click="retry"><el-icon><Refresh /></el-icon>重新识别</el-button>
      <el-button v-if="result.status === 'UNAVAILABLE'" :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新结果</el-button>
    </div>
  </div>

  <div v-loading="loading" class="ocr-layout">
    <section class="surface-panel evidence-panel">
      <div class="panel-heading">
        <div><span class="section-kicker">EVIDENCE / ORIGINAL</span><h2>检测框预览</h2></div>
        <el-button text :disabled="!result" @click="openOriginal"><el-icon><Picture /></el-icon>打开原图</el-button>
      </div>
      <div v-loading="previewLoading" class="preview-placeholder">
        <img v-if="previewUrl" :src="previewUrl" alt="OCR 检测框预览" class="ocr-preview-image" />
        <el-empty v-else :description="previewError || (result?.status === 'FAILED' ? '识别失败，没有可用检测框' : '暂无检测框预览')" :image-size="72" />
      </div>
      <el-alert v-if="result?.status === 'FAILED'" type="error" :closable="false" show-icon :title="result.errorMessage || 'OCR 服务未返回识别结果'" class="ocr-error" />
      <el-alert v-if="result?.status === 'UNAVAILABLE'" type="warning" :closable="false" show-icon :title="result.errorMessage || 'OCR 识别结果暂不可用，请稍后刷新'" class="ocr-error" />
      <div class="text-blocks">
        <span v-for="(block, index) in result?.textBlocks || []" :key="`${block.pageNo}-${index}-${block.text}`">{{ block.text }} <b>{{ (block.confidence * 100).toFixed(0) }}%</b></span>
        <el-empty v-if="!result?.textBlocks?.length" description="暂无文本块" :image-size="50" />
      </div>
    </section>

    <section class="surface-panel field-panel">
      <div class="panel-heading"><div><span class="section-kicker">MAPPED FIELDS</span><h2>业务字段</h2></div><span class="field-count">{{ result?.fields?.length || 0 }} 个字段</span></div>
      <div class="field-list">
        <div v-for="field in result?.fields || []" :key="field.fieldCode" class="field-row" :class="{ review: field.reviewRequired }">
          <div class="field-meta"><code>{{ field.fieldCode }}</code><span :class="field.reviewRequired ? 'confidence low' : 'confidence'">{{ (field.confidence * 100).toFixed(0) }}%</span></div>
          <div v-if="editing === field.fieldCode" class="edit-line"><div class="ocr-edit-field"><el-input v-model="editValue" maxlength="512" /><small v-if="editValueError" class="ocr-edit-error" role="status">{{ editValueError }}</small></div><div class="ocr-edit-field"><el-input v-model="editReason" maxlength="512" placeholder="修订原因（可选）" /><small v-if="editReason.length > 512" class="ocr-edit-error" role="status">修订原因不能超过 512 个字符。</small></div><el-button type="primary" :disabled="Boolean(editValueError) || editReason.length > 512" @click="save(field.fieldCode)">保存</el-button><el-button @click="editing = ''">取消</el-button></div>
          <div v-else class="value-line"><strong>{{ field.value || '未识别' }}</strong><el-tag size="small" :type="field.validationStatus === 'PASSED' ? 'success' : 'warning'" effect="plain">{{ field.validationStatus === 'PASSED' ? '格式通过' : field.validationMessage || '待复核' }}</el-tag><el-button v-if="canRevise" text title="修订字段" @click="beginEdit(field)"><el-icon><EditPen /></el-icon></el-button></div>
        </div>
        <el-empty v-if="!result?.fields?.length" :description="result?.status === 'FAILED' ? '识别失败，重试后再进行字段复核' : 'OCR 尚未返回业务字段'" :image-size="60" />
      </div>
      <div class="revision-footer">已记录 {{ result?.revisions?.length || 0 }} 次人工修订</div>
      <div v-if="showConfirmBar" class="confirm-bar">
        <template v-if="result?.confirmStatus === 'CONFIRMED'">
          <el-tag type="success" effect="plain"><el-icon><Check /></el-icon> 已确认</el-tag>
        </template>
        <template v-else-if="result?.confirmStatus === 'REJECTED'">
          <el-tag type="info" effect="plain">已拒绝</el-tag>
        </template>
        <template v-else>
          <el-button type="primary" :loading="confirming" :disabled="hasBlockingLowConfidence" @click="confirmBackfill">确认回填档案</el-button>
          <el-button type="danger" plain :loading="rejecting" :disabled="confirming || rejecting" @click="rejectBinding">标记为无效</el-button>
          <span v-if="hasBlockingLowConfidence" class="confirm-warn" role="alert">存在低置信度字段，必须人工修正后再确认</span>
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ocr-preview-image { display: block; width: 100%; height: 100%; object-fit: contain; }
.ocr-error { margin: 0 18px 16px; }
.ocr-edit-field { display: grid; gap: 4px; min-width: 0; flex: 1; }
.ocr-edit-error { color: var(--el-color-danger); font-size: 12px; line-height: 1.4; }
.confirm-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--el-border-color-lighter); }
.confirm-warn { color: var(--el-color-danger); font-size: 13px; }
</style>
