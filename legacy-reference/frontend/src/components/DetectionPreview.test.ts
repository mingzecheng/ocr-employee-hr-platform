import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DetectionPreview from './DetectionPreview.vue'

const { getDetectionPreview } = vi.hoisted(() => ({
  getDetectionPreview: vi.fn(),
}))

vi.mock('@/api/archive', () => ({ getDetectionPreview }))

describe('DetectionPreview', () => {
  it('can retry a missing preview without requiring a new upload', async () => {
    getDetectionPreview.mockResolvedValue(new Blob(['preview'], { type: 'image/png' }))
    URL.createObjectURL = vi.fn(() => 'blob:retry-preview')
    URL.revokeObjectURL = vi.fn()
    const wrapper = mount(DetectionPreview, {
      props: { bindingId: 31, preview: null },
      global: { stubs: { 'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' } } },
    })

    expect(wrapper.text()).toContain('检测框效果图暂不可用')
    await wrapper.get('[data-test="retry-preview"]').trigger('click')
    await flushPromises()

    expect(getDetectionPreview).toHaveBeenCalledWith(31)
    expect(wrapper.find('img').attributes('src')).toBe('blob:retry-preview')
  })
})
