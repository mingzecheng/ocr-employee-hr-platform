<template>
  <section v-if="result" class="paper-panel result-panel">
    <div class="panel-heading result-heading">
      <div>
        <h2>OCR 识别结果</h2>
        <p v-if="sourceName || result.engineVersion" class="result-subtitle">
          {{ sourceName || '原始影像' }}<span v-if="result.engineVersion"> · {{ result.engineVersion }}</span>
        </p>
      </div>
      <div class="result-heading-actions">
        <el-button v-if="result.textBlocks.length" text :icon="CopyDocument" data-test="copy-ocr-text" @click="copyText">复制全部文字</el-button>
        <span class="ocr-status" :class="statusClass"><span>●</span>{{ statusLabel }}</span>
      </div>
    </div>
    <div v-if="result.taskId" class="result-meta">任务 {{ result.taskId }} · {{ result.textBlocks.length }} 个检测块 · {{ result.fields.length }} 个结构化字段</div>
    <div v-if="result.status === 'FAILED'" class="ocr-error">{{ result.errorMessage || '识别失败，请检查原始图片' }}</div>
    <div v-if="result.fields.length" class="result-section">
      <div class="result-section-title"><el-icon><Collection /></el-icon><span>结构化字段</span></div>
      <div class="review-summary" data-test="ocr-field-summary">
        <div><span>字段总数</span><strong>{{ result.fields.length }}</strong></div>
        <div data-test="low-confidence-count"><span>低置信度</span><strong>{{ lowConfidenceCount }}</strong></div>
        <div data-test="missing-field-count"><span>未检出</span><strong>{{ missingFieldCount }}</strong></div>
        <div><span>已修订</span><strong>{{ correctedFields.size }}</strong></div>
      </div>
      <div class="field-filter-bar" role="group" aria-label="字段复核筛选">
        <button type="button" class="field-filter" :class="{ active: fieldFilter === 'all' }" data-test="field-filter-all" :aria-pressed="fieldFilter === 'all'" @click="fieldFilter = 'all'">全部 {{ result.fields.length }}</button>
        <button type="button" class="field-filter" :class="{ active: fieldFilter === 'low' }" data-test="field-filter-low" :aria-pressed="fieldFilter === 'low'" @click="fieldFilter = 'low'">低置信度 {{ lowConfidenceCount }}</button>
        <button type="button" class="field-filter" :class="{ active: fieldFilter === 'missing' }" data-test="field-filter-missing" :aria-pressed="fieldFilter === 'missing'" @click="fieldFilter = 'missing'">未检出 {{ missingFieldCount }}</button>
      </div>
      <div v-if="correctionError" class="inline-error" data-test="correction-error">{{ correctionError }}</div>
      <div v-if="filteredFields.length" class="field-list">
        <div v-for="field in filteredFields" :key="field.fieldCode" class="field-item" :data-test="`field-${field.fieldCode}`">
          <div class="field-code">{{ field.fieldCode }}</div>
          <div v-if="editingField === field.fieldCode" style="display: flex; gap: 6px; margin: 8px 0 6px">
            <el-input v-model="editingValue" size="small" aria-label="修订字段值" @keyup.enter="saveField(field.fieldCode)" />
            <el-button text :icon="Check" aria-label="保存字段修订" title="保存字段修订" @click="saveField(field.fieldCode)" />
          </div>
          <div v-else class="field-value">{{ field.value || '未检出' }}</div>
          <div class="confidence">置信度 {{ confidence(field.confidence) }} · {{ field.validationStatus || '未校验' }} <span v-if="isCorrected(field.fieldCode)" class="review-badge">已修订</span> <el-button v-if="editingField !== field.fieldCode" text size="small" :icon="EditPen" aria-label="修订字段" title="修订字段" @click="startEdit(field.fieldCode, field.value)" /></div>
        </div>
      </div>
      <div v-else class="empty-state compact-empty">当前筛选没有需要显示的字段</div>
    </div>
    <div class="result-section">
      <div class="result-section-title"><el-icon><Document /></el-icon><span>文字检测块 · {{ result.textBlocks.length }}</span></div>
      <div v-if="result.textBlocks.length" class="text-block-list">
        <div v-for="(block, index) in result.textBlocks" :key="`${block.text}-${index}`" class="text-block">
          <span class="block-number">{{ String(index + 1).padStart(2, '0') }}</span><span>{{ block.text || '空文本' }}</span><span class="confidence">{{ confidence(block.confidence) }}</span>
        </div>
      </div>
      <div v-else class="empty-state">未返回文字检测块</div>
    </div>
    <div class="result-section">
      <div class="result-section-title"><el-icon><Aim /></el-icon><span>检测框效果图</span></div>
      <DetectionPreview :binding-id="result.bindingId" :preview="result.detectionPreview" />
    </div>
  </section>
  <section v-else class="paper-panel empty-state">上传图片并开始识别后，结果会显示在这里</section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Aim, Check, Collection, CopyDocument, Document, EditPen } from '@element-plus/icons-vue'
import DetectionPreview from './DetectionPreview.vue'
import type { FieldResult, OcrResult } from '@/types/api'

defineOptions({ name: 'OcrResultPanel' })
const props = defineProps<{
  result: OcrResult | null
  onCorrectField?: (fieldCode: string, value: string) => Promise<unknown>
}>()
const emit = defineEmits<{ correctField: [fieldCode: string, value: string] }>()
const statusClass = computed(() => props.result?.status === 'SUCCEEDED' ? 'success' : props.result?.status === 'FAILED' ? 'failed' : 'pending')
const statusLabel = computed(() => props.result?.status === 'SUCCEEDED' ? '识别成功' : props.result?.status === 'FAILED' ? '识别失败' : '处理中')
const sourceName = computed(() => {
  const value = props.result?.sourceFile?.originalName
  return typeof value === 'string' ? value : ''
})
const editingField = ref<string | null>(null)
const editingValue = ref('')
const fieldFilter = ref<'all' | 'low' | 'missing'>('all')
const correctionError = ref('')
const correctedFields = ref(new Set<string>())
const isMissing = (field: FieldResult) => !field.value?.trim()
const isLowConfidence = (field: FieldResult) => field.confidence == null || field.confidence < 0.8
const lowConfidenceCount = computed(() => props.result?.fields.filter(isLowConfidence).length || 0)
const missingFieldCount = computed(() => props.result?.fields.filter(isMissing).length || 0)
const filteredFields = computed(() => {
  const fields = props.result?.fields || []
  if (fieldFilter.value === 'low') return fields.filter(isLowConfidence)
  if (fieldFilter.value === 'missing') return fields.filter(isMissing)
  return fields
})
function confidence(value: number | null | undefined) { return value == null ? '—' : `${Math.round(value * 100)}%` }
function startEdit(fieldCode: string, value: string | null) { correctionError.value = ''; editingField.value = fieldCode; editingValue.value = value || '' }
function isCorrected(fieldCode: string) { return correctedFields.value.has(fieldCode) }
async function saveField(fieldCode: string) {
  const value = editingValue.value.trim()
  if (!value) { correctionError.value = '字段值不能为空'; return }
  correctionError.value = ''
  try {
    if (props.onCorrectField) await props.onCorrectField(fieldCode, value)
    else emit('correctField', fieldCode, value)
    correctedFields.value = new Set(correctedFields.value).add(fieldCode)
    editingField.value = null
  } catch (error) {
    correctionError.value = error instanceof Error ? error.message : '字段修订保存失败'
  }
}
watch(() => props.result?.bindingId, () => { correctedFields.value = new Set(); editingField.value = null; correctionError.value = '' })
async function copyText() {
  const text = props.result?.textBlocks.map((block) => block.text.trim()).filter(Boolean).join('\n') || ''
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('识别文字已复制')
  } catch {
    ElMessage.warning('当前浏览器不支持自动复制，请手动选择文字')
  }
}
</script>
