<template>
  <div>
    <div class="archive-header">
      <div>
        <p class="eyebrow">Archive / Evidence</p>
        <h1>{{ employee?.name || '员工档案' }}</h1>
        <p class="heading-copy"><span class="archive-id">{{ employee?.employeeNo || `EMP-${route.params.id}` }}</span> · 原始材料与 OCR 证据</p>
      </div>
      <el-button text :icon="ArrowLeft" @click="router.push({ name: 'employees' })">返回员工列表</el-button>
    </div>

    <div class="archive-grid">
      <div class="archive-side">
        <section class="paper-panel upload-panel">
          <div class="panel-heading" style="margin: -22px -22px 22px"><h2>上传材料</h2></div>
          <input ref="fileInput" type="file" accept="image/png,image/jpeg" hidden @change="onFileChange" />
          <label class="upload-type-select">
            <span>材料类型</span>
            <select v-model="documentType" data-test="document-type" aria-label="材料类型">
              <option value="EMPLOYEE_PROFILE">员工登记表</option>
              <option value="ONBOARDING_FORM">入职登记表</option>
              <option value="TRANSFER_FORM">异动申请表</option>
              <option value="RESIGNATION_HANDOVER">离职交接表</option>
            </select>
          </label>
          <div class="upload-drop" @click="fileInput?.click()">
            <el-icon><UploadFilled /></el-icon>
            <div class="upload-title">{{ selectedFile?.name || '选择 PNG / JPEG 图片' }}</div>
            <div class="upload-note">识别会保留原图，并生成带检测框的效果图</div>
          </div>
          <el-button type="primary" data-test="recognize-button" :loading="working" :disabled="!selectedFile" :icon="MagicStick" style="width: 100%; margin-top: 16px" @click="recognize">{{ working ? '正在识别' : '开始 OCR 识别' }}</el-button>
          <p v-if="uploadError" class="login-error">{{ uploadError }}</p>
        </section>

        <section class="paper-panel materials-panel">
          <div class="panel-heading">
            <div>
              <p class="eyebrow">Source files</p>
              <h2>档案材料</h2>
            </div>
            <el-button text :icon="Refresh" :loading="documentsLoading" aria-label="刷新档案材料" title="刷新档案材料" @click="loadDocuments" />
          </div>
          <div v-if="documentsError" class="preview-error">{{ documentsError }}</div>
          <div v-else-if="documentsLoading" class="empty-state">正在加载材料目录</div>
          <div v-else-if="!documents.length" data-test="archive-empty" class="empty-state">暂无档案材料</div>
          <div v-else class="document-list">
            <article v-for="document in documents" :key="document.id" :data-test="`document-${document.id}`" class="document-item">
              <div class="document-summary">
                <div class="document-main">
                  <span class="document-type">{{ document.documentType }}</span>
                  <h3>{{ document.title }}</h3>
                  <p class="document-meta">最新版本 {{ document.latestVersionNo ? `v${document.latestVersionNo}` : '—' }} · {{ document.latestFileName || '未关联文件' }} · {{ formatBytes(document.latestSize) }}</p>
                </div>
                <el-button text :icon="expandedDocumentId === document.id ? ArrowUp : ArrowDown" data-test="show-versions" @click="toggleVersions(document)">{{ expandedDocumentId === document.id ? '收起版本' : '查看版本' }}</el-button>
              </div>
              <div v-if="expandedDocumentId === document.id" class="version-list">
                <div v-if="versionsLoading === document.id" class="empty-state compact-empty">正在加载版本历史</div>
                <div v-else-if="versionsError[document.id]" class="preview-error">{{ versionsError[document.id] }}</div>
                <div v-else-if="!versionsByDocument[document.id]?.length" class="empty-state compact-empty">暂无版本记录</div>
                <div v-else v-for="version in versionsByDocument[document.id]" :key="version.id" :data-test="`version-${version.id}`" class="version-item">
                  <div>
                    <strong>版本 {{ version.versionNo }}</strong>
                    <p class="document-meta">{{ version.originalName }} · {{ formatBytes(version.sizeBytes) }} · {{ version.status }}</p>
                    <p class="version-hash">SHA-256 {{ version.sha256 }}</p>
                  </div>
                  <div class="version-actions">
                    <el-button text :icon="MagicStick" data-test="view-ocr" :loading="ocrLoadingVersionId === version.id" @click="viewOcr(version, document.documentType)">查看 OCR</el-button>
                    <el-button text :icon="View" data-test="preview-version" @click="previewVersion(version)">预览</el-button>
                    <el-button text :icon="Download" :loading="downloadingVersionId === version.id" aria-label="下载原始文件" title="下载原始文件" @click="downloadVersion(version)" />
                  </div>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section v-if="previewUrl || previewLoading || previewError" class="paper-panel source-preview-panel">
          <div class="panel-heading">
            <div><p class="eyebrow">Original file</p><h2>原始影像</h2></div>
            <span v-if="previewName" class="document-meta">{{ previewName }}</span>
          </div>
          <div v-if="previewLoading" class="empty-state">正在加载原始影像</div>
          <div v-else-if="previewError" class="preview-error">{{ previewError }}</div>
          <div v-else class="preview-frame">
            <img :src="previewUrl" :alt="`原始文件 ${previewName}`" data-test="version-preview" />
          </div>
        </section>
      </div>

      <OcrResultPanel :result="result" :on-correct-field="correctField" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, ArrowLeft, ArrowUp, Download, MagicStick, Refresh, UploadFilled, View } from '@element-plus/icons-vue'
import {
  correctField as correctOcrField,
  getOcrResult,
  getVersionFile,
  listArchiveDocuments,
  listArchiveVersions,
  listEmployees,
  runOcr,
  uploadDocument,
} from '@/api/archive'
import OcrResultPanel from '@/components/OcrResultPanel.vue'
import type { ArchiveDocument, ArchiveVersion, Employee, OcrResult } from '@/types/api'

const route = useRoute()
const router = useRouter()
const fileInput = ref<HTMLInputElement>()
const employee = ref<Employee | null>(null)
const selectedFile = ref<File | null>(null)
const documentType = ref('EMPLOYEE_PROFILE')
const result = ref<OcrResult | null>(null)
const working = ref(false)
const uploadError = ref('')
const documents = ref<ArchiveDocument[]>([])
const documentsLoading = ref(false)
const documentsError = ref('')
const expandedDocumentId = ref<number | null>(null)
const versionsByDocument = ref<Record<number, ArchiveVersion[]>>({})
const versionsError = ref<Record<number, string>>({})
const versionsLoading = ref<number | null>(null)
const previewUrl = ref('')
const previewName = ref('')
const previewLoading = ref(false)
const previewError = ref('')
const downloadingVersionId = ref<number | null>(null)
const ocrLoadingVersionId = ref<number | null>(null)

function onFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!['image/png', 'image/jpeg'].includes(file.type)) {
    uploadError.value = '仅支持 PNG 或 JPEG 图片'
    selectedFile.value = null
    return
  }
  selectedFile.value = file
  uploadError.value = ''
}

async function loadDocuments() {
  documentsLoading.value = true
  documentsError.value = ''
  try {
    documents.value = await listArchiveDocuments(Number(route.params.id))
  } catch (error) {
    documentsError.value = error instanceof Error ? error.message : '档案材料加载失败'
  } finally {
    documentsLoading.value = false
  }
}

async function toggleVersions(document: ArchiveDocument) {
  if (expandedDocumentId.value === document.id) {
    expandedDocumentId.value = null
    return
  }
  expandedDocumentId.value = document.id
  if (versionsByDocument.value[document.id]) return
  versionsLoading.value = document.id
  versionsError.value = { ...versionsError.value, [document.id]: '' }
  try {
    versionsByDocument.value = {
      ...versionsByDocument.value,
      [document.id]: await listArchiveVersions(document.id),
    }
  } catch (error) {
    versionsError.value = {
      ...versionsError.value,
      [document.id]: error instanceof Error ? error.message : '版本历史加载失败',
    }
  } finally {
    versionsLoading.value = null
  }
}

function releasePreviewUrl() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

async function fetchVersionFile(version: ArchiveVersion) {
  releasePreviewUrl()
  previewName.value = version.originalName
  previewError.value = ''
  previewLoading.value = true
  try {
    previewUrl.value = URL.createObjectURL(await getVersionFile(version.id))
  } catch (error) {
    previewError.value = error instanceof Error ? error.message : '原始影像加载失败'
  } finally {
    previewLoading.value = false
  }
}

async function previewVersion(version: ArchiveVersion) {
  await fetchVersionFile(version)
}

async function viewOcr(version: ArchiveVersion, materialType: string) {
  ocrLoadingVersionId.value = version.id
  uploadError.value = ''
  try {
    result.value = await getOcrResult(version.id)
    await fetchVersionFile(version)
  } catch (error) {
    if (httpStatus(error) === 403) {
      try {
        result.value = await runOcr(version.id, materialType)
        await fetchVersionFile(version)
        ElMessage.success('已为该版本生成 OCR 结果')
        return
      } catch (retryError) {
        ElMessage.error(retryError instanceof Error ? retryError.message : '该版本暂时无法识别')
        return
      }
    }
    ElMessage.error(error instanceof Error ? error.message : '历史 OCR 结果加载失败')
  } finally {
    ocrLoadingVersionId.value = null
  }
}

function httpStatus(error: unknown): number | null {
  if (typeof error !== 'object' || error === null || !('response' in error)) return null
  const response = error.response
  if (typeof response !== 'object' || response === null || !('status' in response)) return null
  return typeof response.status === 'number' ? response.status : null
}

async function downloadVersion(version: ArchiveVersion) {
  downloadingVersionId.value = version.id
  try {
    await fetchVersionFile(version)
    if (!previewUrl.value) return
    const link = document.createElement('a')
    link.href = previewUrl.value
    link.download = version.originalName || `archive-version-${version.versionNo}`
    link.click()
  } catch (error) {
    previewError.value = error instanceof Error ? error.message : '原始文件下载失败'
  } finally {
    downloadingVersionId.value = null
  }
}

async function recognize() {
  if (!selectedFile.value) return
  working.value = true
  uploadError.value = ''
  try {
    const uploaded = await uploadDocument(Number(route.params.id), selectedFile.value, documentType.value)
    result.value = await runOcr(uploaded.versionId, documentType.value)
    await loadDocuments()
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
    ElMessage.success('OCR 识别已完成')
  } catch (error) {
    uploadError.value = error instanceof Error ? error.message : '上传或识别失败'
  } finally {
    working.value = false
  }
}

async function correctField(fieldCode: string, value: string) {
  if (!result.value) return
  try {
    result.value = await correctOcrField(result.value.bindingId, fieldCode, value, '管理端人工修订')
    ElMessage.success('字段修订已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '字段修订保存失败')
    throw error
  }
}

function formatBytes(size: number | null) {
  if (size == null) return '大小未知'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

onMounted(async () => {
  await Promise.all([
    listEmployees(1, 100).then((employees) => {
      employee.value = employees.items.find((item) => item.id === Number(route.params.id)) || null
    }).catch(() => { uploadError.value = '员工资料加载失败' }),
    loadDocuments(),
  ])
})

onBeforeUnmount(releasePreviewUrl)
</script>
