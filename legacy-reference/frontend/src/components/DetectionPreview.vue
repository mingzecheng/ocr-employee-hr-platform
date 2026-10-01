<template>
  <div>
    <div v-if="loading" class="preview-empty">正在加载检测框效果图…</div>
    <div v-else-if="errorMessage" class="preview-error">
      <span>{{ errorMessage }}</span>
      <el-button text data-test="retry-preview" :icon="Refresh" @click="load(true)">重新加载</el-button>
    </div>
    <div v-else-if="!preview && !previewUrl" class="preview-empty">
      <span>检测框效果图暂不可用</span>
      <el-button text data-test="retry-preview" :icon="Refresh" @click="load(true)">重新检查</el-button>
    </div>
    <div v-else class="preview-frame">
      <img :src="previewUrl" alt="OCR 文字检测框效果图" />
      <div style="display: flex; justify-content: flex-end; padding: 7px 9px; background: #fbfaf6; border-top: 1px solid #d8ddd5">
        <el-button text :icon="Download" aria-label="下载检测框效果图" title="下载检测框效果图" @click="download" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getDetectionPreview } from '@/api/archive'
import type { DetectionPreview } from '@/types/api'
import { Download, Refresh } from '@element-plus/icons-vue'

defineOptions({ name: 'DetectionPreview' })
const props = defineProps<{ bindingId: number; preview: DetectionPreview | null }>()
const previewUrl = ref('')
const loading = ref(false)
const errorMessage = ref('')

async function load(force = false) {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  if (!props.preview && !force) return
  loading.value = true
  errorMessage.value = ''
  try { previewUrl.value = URL.createObjectURL(await getDetectionPreview(props.bindingId)) } catch { errorMessage.value = '检测框效果图暂不可用' } finally { loading.value = false }
}
function download() {
  if (!previewUrl.value) return
  const link = document.createElement('a')
  link.href = previewUrl.value
  link.download = `ocr-detection-${props.bindingId}.png`
  link.click()
}
watch(() => [props.bindingId, props.preview], () => load(), { immediate: true })
onBeforeUnmount(() => { if (previewUrl.value) URL.revokeObjectURL(previewUrl.value) })
</script>
