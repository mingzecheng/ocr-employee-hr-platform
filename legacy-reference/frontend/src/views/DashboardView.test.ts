import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DashboardView from './DashboardView.vue'
import type { StatisticsOverview, TodoData } from '@/types/api'

const statistics: StatisticsOverview = {
  activeEmployeeCount: 12,
  archiveDocumentCount: 19,
  ocrTaskCount: 8,
  ocrSucceededCount: 6,
  ocrFailedCount: 2,
  ocrSuccessRate: 0.75,
  archiveCompletenessRate: 0.5,
  pendingReviewCount: 3,
}

const { getStatisticsOverview, push } = vi.hoisted(() => ({
  getStatisticsOverview: vi.fn(),
  push: vi.fn(),
}))

const { getTodos } = vi.hoisted(() => ({
  getTodos: vi.fn(),
}))

vi.mock('@/api/statistics', () => ({
  getStatisticsOverview,
}))

vi.mock('@/api/todos', () => ({
  getTodos,
}))

vi.mock('@/api/archive', () => ({
  listEmployees: vi.fn().mockResolvedValue({
    items: [{ id: 1, employeeNo: 'E-001', name: '张三', idCardNo: null, departmentId: 1, status: 'ACTIVE' }],
    total: 1,
    page: 1,
    pageSize: 20,
  }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

describe('DashboardView', () => {
  const todos: TodoData = {
    items: [],
    total: 4,
    pendingApprovalCount: 2,
    dueSoonCount: 1,
    overdueCount: 1,
    ocrFailedCount: 1,
    generatedAt: '2026-09-20T10:00:00',
  }

  it('renders real overview metrics after loading', async () => {
    getStatisticsOverview.mockResolvedValue(statistics)
    getTodos.mockResolvedValue(todos)
    const wrapper = mount(DashboardView, {
      global: { stubs: { 'el-button': true, 'el-icon': true } },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('50%')
    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('4')
    expect(wrapper.get('[data-test="ocr-health"]').text()).toContain('75%')
    expect(wrapper.get('[data-test="ocr-health"]').text()).toContain('2 个失败任务')
    expect(wrapper.get('[data-test="todo-summary"] [aria-label="待审批 2"]').exists()).toBe(true)
  })

  it('keeps an explicit error state when overview loading fails', async () => {
    getStatisticsOverview.mockRejectedValue(new Error('network error'))
    getTodos.mockResolvedValue(todos)
    const wrapper = mount(DashboardView, {
      global: { stubs: { 'el-button': true, 'el-icon': true } },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('统计数据暂时无法加载')
  })

  it('exposes shortcuts for the four operational workbenches', async () => {
    getStatisticsOverview.mockResolvedValue(statistics)
    getTodos.mockResolvedValue(todos)
    const wrapper = mount(DashboardView, {
      global: { stubs: { 'el-button': true, 'el-icon': true } },
    })

    await flushPromises()

    await wrapper.get('[data-test="quick-action-create-employee"]').trigger('click')
    await wrapper.get('[data-test="quick-action-workflow"]').trigger('click')
    await wrapper.get('[data-test="quick-action-access"]').trigger('click')
    await wrapper.get('[data-test="quick-action-inventory"]').trigger('click')

    expect(push).toHaveBeenNthCalledWith(1, { name: 'employees', query: { action: 'create' } })
    expect(push).toHaveBeenNthCalledWith(2, { name: 'workflow' })
    expect(push).toHaveBeenNthCalledWith(3, { name: 'archive-access' })
    expect(push).toHaveBeenNthCalledWith(4, { name: 'inventory' })
  })
})
