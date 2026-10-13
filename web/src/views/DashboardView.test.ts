import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import DashboardView from './DashboardView.vue'

vi.mock('../api/http', () => ({
  request: vi.fn(({ url }: { url: string }) => Promise.resolve(
    url === '/todos'
      ? { items: [], total: 1, pendingApprovalCount: 1, dueSoonCount: 0, overdueCount: 0, ocrFailedCount: 1 }
      : url === '/contracts'
        ? { items: [], total: 2, page: 1, pageSize: 1 }
        : url === '/statistics/report'
          ? { monthlyTrend: [], departments: [], workflowCycle: { completedCount: 0, averageDurationHours: 0, shortestDurationHours: 0, longestDurationHours: 0 } }
        : {
          employeeCount: 3, completeArchiveCount: 2, archiveCount: 3, archiveCompleteness: 2 / 3,
          workflowCount: 4, pendingWorkflowCount: 1, activeAccessCount: 1, abnormalAccessCount: 0,
          ocrSucceededCount: 5, ocrFailedCount: 1, ocrReviewCount: 2, averageOcrDurationMs: 120,
          generatedAt: '2026-10-01T00:00:00',
        },
  )),
}))

describe('DashboardView', () => {
  it('shows operational task metrics after loading', async () => {
    const wrapper = mount(DashboardView, { global: { plugins: [ElementPlus], stubs: { RouterLink: true } } })
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(wrapper.text()).toContain('人事运营总览')
    expect(wrapper.text()).toContain('待处理审批')
    expect(wrapper.text()).toContain('OCR 待复核')
    expect(wrapper.text()).toContain('合同待续签')
    expect(wrapper.text()).toContain('未来 60 天内到期')
  })
})
