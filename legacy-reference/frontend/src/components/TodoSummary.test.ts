import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import TodoSummary from './TodoSummary.vue'
import type { TodoData } from '@/types/api'

const data: TodoData = {
  items: [],
  total: 7,
  pendingApprovalCount: 3,
  dueSoonCount: 2,
  overdueCount: 1,
  ocrFailedCount: 1,
  generatedAt: '2026-09-20T10:00:00',
}

const { push } = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

describe('TodoSummary', () => {
  it('renders the compact counts and links to the todo center', async () => {
    const wrapper = mount(TodoSummary, {
      props: { data, loading: false, error: '' },
      global: {
        stubs: { 'el-icon': true },
      },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('7')
    expect(wrapper.get('[aria-label="待审批 3"]').exists()).toBe(true)
    expect(wrapper.get('[aria-label="即将到期 2"]').exists()).toBe(true)
    expect(wrapper.get('[aria-label="OCR 失败 1"]').exists()).toBe(true)
    await wrapper.get('[data-test="todo-summary-link"]').trigger('click')
    expect(push).toHaveBeenCalledWith({ name: 'todos' })
  })
})
