import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { request } from '../api/http'
import OcrReviewView from './OcrReviewView.vue'

vi.mock('../api/http', () => ({
  http: { get: vi.fn() },
  request: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { versionId: '31' }, query: {} }),
  useRouter: () => ({ back: vi.fn() }),
}))
const authState = vi.hoisted(() => ({ roles: ['EMPLOYEE'] as string[] }))
vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({ roles: authState.roles }),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { confirm: vi.fn(), prompt: vi.fn(), alert: vi.fn() } }
})

const requestMock = vi.mocked(request)
const confirmBox = vi.mocked(ElMessageBox.confirm)

function baseResult(over: Record<string, unknown> = {}) {
  return {
    bindingId: 12,
    versionId: 31,
    status: 'SUCCEEDED',
    documentType: 'employee_profile',
    processingDurationMs: 20,
    reviewRequired: false,
    textBlocks: [],
    rawFields: [],
    fields: [
      { fieldCode: 'employee_name', value: '林晓', confidence: 0.95, pageNo: 1, validationStatus: 'PASSED', reviewRequired: false },
      { fieldCode: 'phone', value: '13800138000', confidence: 0.93, pageNo: 1, validationStatus: 'PASSED', reviewRequired: false },
    ],
    revisions: [],
    ...over,
  }
}

beforeEach(() => {
  authState.roles = ['EMPLOYEE']
  confirmBox.mockReset()
  confirmBox.mockResolvedValue({ value: 'confirm', action: 'confirm' } as never)
})

afterEach(() => {
  requestMock.mockReset()
  document.body.innerHTML = ''
})

describe('OcrReviewView', () => {
  it('keeps field correction controls hidden for employees', async () => {
    requestMock.mockResolvedValue({
      bindingId: undefined,
      versionId: 31,
      status: 'SUCCEEDED',
      documentType: 'employee_profile',
      processingDurationMs: 20,
      reviewRequired: true,
      textBlocks: [],
      rawFields: [],
      fields: [{
        fieldCode: 'employee_id', value: 'EMP-031', confidence: 0.72,
        pageNo: 1, validationStatus: 'PASSED', reviewRequired: true,
      }],
      revisions: [],
    } as never)

    const wrapper = mount(OcrReviewView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('EMP-031')
    expect(wrapper.find('button[title="修订字段"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows an explicit refresh state when the OCR result service is unavailable', async () => {
    requestMock.mockResolvedValue({
      bindingId: undefined,
      versionId: 31,
      status: 'UNAVAILABLE',
      documentType: 'employee_profile',
      processingDurationMs: 20,
      reviewRequired: true,
      errorCode: 'OCR_RESULT_UNAVAILABLE',
      errorMessage: 'OCR 识别结果暂不可用，请稍后刷新',
      textBlocks: [], rawFields: [], fields: [], revisions: [],
    } as never)

    const wrapper = mount(OcrReviewView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('结果暂不可用')
    expect(wrapper.text()).toContain('刷新结果')
    wrapper.unmount()
  })

  it('does not show retry to employees when recognition failed', async () => {
    requestMock.mockResolvedValue({
      bindingId: undefined,
      versionId: 31,
      status: 'FAILED',
      documentType: 'employee_profile',
      processingDurationMs: 0,
      reviewRequired: true,
      errorCode: 'OCR_UNAVAILABLE',
      errorMessage: 'OCR 服务暂不可用',
      textBlocks: [], rawFields: [], fields: [], revisions: [],
    } as never)

    const wrapper = mount(OcrReviewView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('识别失败')
    expect(wrapper.text()).not.toContain('重新识别')
    wrapper.unmount()
  })
})

describe('OcrReviewView confirm / reject (HR_ADMIN)', () => {
  beforeEach(() => {
    authState.roles = ['HR_ADMIN']
  })

  it('offers confirm and reject actions to HR when all whitelist fields are confident and passed', async () => {
    requestMock.mockResolvedValue(baseResult() as never)

    const wrapper = mount(OcrReviewView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    const confirmBtn = wrapper.findAll('button').find((b) => b.text().includes('确认回填档案'))
    const rejectBtn = wrapper.findAll('button').find((b) => b.text().includes('标记为无效'))
    expect(confirmBtn).toBeTruthy()
    expect(rejectBtn).toBeTruthy()
    expect(confirmBtn!.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).not.toContain('存在低置信度字段')
    wrapper.unmount()
  })

  it('hides confirm/reject actions for non-HR roles', async () => {
    authState.roles = ['EMPLOYEE']
    requestMock.mockResolvedValue(baseResult() as never)

    const wrapper = mount(OcrReviewView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.findAll('button').some((b) => b.text().includes('确认回填档案'))).toBe(false)
    wrapper.unmount()
  })

  it('disables the confirm button while a whitelist field is low-confidence and not human-revised', async () => {
    requestMock.mockResolvedValue(baseResult({
      fields: [{ fieldCode: 'employee_name', value: '林晓', confidence: 0.55, pageNo: 1, validationStatus: 'PASSED', reviewRequired: true }],
      revisions: [],
    }) as never)

    const wrapper = mount(OcrReviewView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    const confirmBtn = wrapper.findAll('button').find((b) => b.text().includes('确认回填档案'))
    expect(confirmBtn!.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('存在低置信度字段，必须人工修正后再确认')
    wrapper.unmount()
  })

  it('re-enables confirm once the low-confidence field has been human-revised', async () => {
    requestMock.mockResolvedValue(baseResult({
      fields: [{ fieldCode: 'employee_name', value: '林晓', confidence: 0.55, pageNo: 1, validationStatus: 'PASSED', reviewRequired: true }],
      revisions: [{ id: 1, fieldCode: 'employee_name', previousValue: '林哓', currentValue: '林晓', reason: '修正姓名', createdAt: '2026-10-13T09:00:00' }],
    }) as never)

    const wrapper = mount(OcrReviewView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    const confirmBtn = wrapper.findAll('button').find((b) => b.text().includes('确认回填档案'))
    expect(confirmBtn!.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).not.toContain('存在低置信度字段')
    wrapper.unmount()
  })

  it('shows a confirmed status tag instead of actions after the result is already confirmed', async () => {
    requestMock.mockResolvedValue(baseResult({ confirmStatus: 'CONFIRMED' }) as never)

    const wrapper = mount(OcrReviewView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('已确认')
    expect(wrapper.findAll('button').some((b) => b.text().includes('确认回填档案'))).toBe(false)
    wrapper.unmount()
  })

  it('shows a rejected status tag instead of actions after the result is already rejected', async () => {
    requestMock.mockResolvedValue(baseResult({ confirmStatus: 'REJECTED' }) as never)

    const wrapper = mount(OcrReviewView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('已拒绝')
    expect(wrapper.findAll('button').some((b) => b.text().includes('标记为无效'))).toBe(false)
    wrapper.unmount()
  })

  it('calls the confirm endpoint after the dialog and flips to the confirmed tag', async () => {
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/archive/versions/31/ocr-result') return baseResult() as never
      if (config.url === '/archive/ocr-bindings/12/confirm') {
        return { bindingId: 12, confirmStatus: 'CONFIRMED', employeeId: 7, employeeUpdated: true } as never
      }
      return undefined as never
    })

    const wrapper = mount(OcrReviewView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    const confirmBtn = wrapper.findAll('button').find((b) => b.text().includes('确认回填档案'))
    await confirmBtn!.trigger('click')
    await flushPromises()

    expect(confirmBox).toHaveBeenCalled()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/archive/ocr-bindings/12/confirm', method: 'POST',
    }))
    expect(wrapper.text()).toContain('已确认')
    wrapper.unmount()
  })
})
