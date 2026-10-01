import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import OcrResultPanel from './OcrResultPanel.vue'
import type { OcrResult } from '@/types/api'

vi.mock('@/api/archive', () => ({
  getDetectionPreview: vi.fn().mockResolvedValue(new Blob(['preview'], { type: 'image/png' })),
}))

const successResult: OcrResult = {
  bindingId: 31,
  versionId: 21,
  taskId: 'task-1',
  status: 'SUCCEEDED',
  engineVersion: 'paddleocr-local',
  sourceFile: { originalName: 'profile.png' },
  detectionPreview: {
    url: '/api/archive/ocr-bindings/31/detection-preview',
    contentType: 'image/png',
    size: 1200,
    sha256: 'abc',
    width: 800,
    height: 600,
  },
  textBlocks: [{ text: '张三', confidence: 0.98, bbox: [[10, 10], [80, 10], [80, 40], [10, 40]] }],
  fields: [{ fieldCode: 'name', value: '张三', confidence: 0.98, bbox: null, validationStatus: 'VALID' }],
  errorMessage: null,
}

const reviewResult: OcrResult = {
  ...successResult,
  fields: [
    { fieldCode: 'name', value: '张三', confidence: 0.98, bbox: null, validationStatus: 'VALID' },
    { fieldCode: 'id_card', value: null, confidence: null, bbox: null, validationStatus: 'MISSING' },
    { fieldCode: 'department', value: '运营中心', confidence: 0.62, bbox: null, validationStatus: 'REVIEW' },
  ],
}

const ButtonStub = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots, emit }) {
    return () => h('button', { ...attrs, type: 'button', onClick: (event: MouseEvent) => emit('click', event) }, slots.default?.())
  },
})

const InputStub = defineComponent({
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(props, { attrs, emit }) {
    return () => h('input', {
      'aria-label': attrs['aria-label'],
      value: props.modelValue,
      onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
    })
  },
})

const interactiveStubs = { 'el-icon': true, 'el-button': ButtonStub, 'el-input': InputStub }

describe('OcrResultPanel', () => {
  it('shows recognized text, fields, confidence and detection preview', () => {
    const wrapper = mount(OcrResultPanel, { props: { result: successResult }, global: { stubs: { 'el-icon': true, 'el-button': true, 'el-input': true } } })

    expect(wrapper.text()).toContain('识别成功')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('98%')
    expect(wrapper.findComponent({ name: 'DetectionPreview' }).exists()).toBe(true)
  })

  it('shows the OCR failure reason without rendering a successful result state', () => {
    const wrapper = mount(OcrResultPanel, {
      props: {
        result: { ...successResult, status: 'FAILED', errorMessage: '图片无法解析' },
      },
      global: { stubs: { 'el-icon': true, 'el-button': true, 'el-input': true } },
    })

    expect(wrapper.text()).toContain('识别失败')
    expect(wrapper.text()).toContain('图片无法解析')
    expect(wrapper.text()).not.toContain('识别成功')
  })

  it('keeps a successful result usable when the detection preview is missing', () => {
    const wrapper = mount(OcrResultPanel, {
      props: { result: { ...successResult, detectionPreview: null } },
      global: { stubs: { 'el-icon': true, 'el-button': true, 'el-input': true } },
    })

    expect(wrapper.text()).toContain('识别成功')
    expect(wrapper.text()).toContain('检测框效果图暂不可用')
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('copies all recognized text as one readable document', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    const wrapper = mount(OcrResultPanel, { props: { result: successResult }, global: { stubs: { 'el-icon': true, 'el-button': true, 'el-input': true } } })

    await wrapper.get('[data-test="copy-ocr-text"]').trigger('click')

    expect(writeText).toHaveBeenCalledWith('张三')
  })

  it('summarizes fields and filters low-confidence and missing values', async () => {
    const wrapper = mount(OcrResultPanel, { props: { result: reviewResult }, global: { stubs: interactiveStubs } })

    expect(wrapper.get('[data-test="low-confidence-count"]').text()).toContain('2')
    expect(wrapper.get('[data-test="missing-field-count"]').text()).toContain('1')

    await wrapper.get('[data-test="field-filter-low"]').trigger('click')
    expect(wrapper.find('[data-test="field-name"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="field-id_card"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="field-department"]').exists()).toBe(true)

    await wrapper.get('[data-test="field-filter-missing"]').trigger('click')
    expect(wrapper.find('[data-test="field-id_card"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="field-department"]').exists()).toBe(false)
  })

  it('keeps the edit value and marks a field revised after a correction succeeds', async () => {
    const onCorrectField = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(OcrResultPanel, {
      props: { result: successResult, onCorrectField },
      global: { stubs: interactiveStubs },
    })

    await wrapper.get('[aria-label="修订字段"]').trigger('click')
    await wrapper.get('[aria-label="修订字段值"]').setValue('李四')
    await wrapper.get('[aria-label="保存字段修订"]').trigger('click')
    await flushPromises()

    expect(onCorrectField).toHaveBeenCalledWith('name', '李四')
    expect(wrapper.text()).toContain('已修订')
    expect(wrapper.find('[aria-label="修订字段值"]').exists()).toBe(false)
  })

  it('retains the edit value and edit mode when correction fails', async () => {
    const onCorrectField = vi.fn().mockRejectedValue(new Error('字段修订保存失败'))
    const wrapper = mount(OcrResultPanel, {
      props: { result: successResult, onCorrectField },
      global: { stubs: interactiveStubs },
    })

    await wrapper.get('[aria-label="修订字段"]').trigger('click')
    await wrapper.get('[aria-label="修订字段值"]').setValue('李四')
    await wrapper.get('[aria-label="保存字段修订"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[aria-label="修订字段值"]').element).toHaveProperty('value', '李四')
    expect(wrapper.text()).toContain('字段修订保存失败')
  })
})
