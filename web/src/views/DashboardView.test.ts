import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import DashboardView from './DashboardView.vue'

vi.mock('../api/http', () => ({
  request: vi.fn().mockResolvedValue({
    employeeCount: 3, completeArchiveCount: 2, archiveCount: 3, archiveCompleteness: 2 / 3,
    workflowCount: 4, pendingWorkflowCount: 1, activeAccessCount: 1, abnormalAccessCount: 0,
    ocrSucceededCount: 5, ocrFailedCount: 1, ocrReviewCount: 2, averageOcrDurationMs: 120,
    generatedAt: '2026-10-01T00:00:00',
  }),
}))

describe('DashboardView', () => {
  it('shows scoped summary metrics after loading', async () => {
    const wrapper = mount(DashboardView, { global: { plugins: [ElementPlus], stubs: { RouterLink: true } } })
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(wrapper.text()).toContain('业务总览')
    expect(wrapper.text()).toContain('档案完整率')
    expect(wrapper.text()).toContain('67%')
  })
})
