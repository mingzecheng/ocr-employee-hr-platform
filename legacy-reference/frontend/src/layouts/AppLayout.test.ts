import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import AppLayout from './AppLayout.vue'

const { getTodos, replace } = vi.hoisted(() => ({
  getTodos: vi.fn(),
  replace: vi.fn(),
}))

vi.mock('@/api/todos', () => ({ getTodos }))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: { username: 'admin', displayName: '管理员' },
    isAuthenticated: true,
    logout: vi.fn(),
  }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ meta: { label: '概览' } }),
  useRouter: () => ({ replace }),
}))

const RouterLinkStub = defineComponent({
  setup(_, { slots }) {
    return () => h('a', slots.default?.())
  },
})

describe('AppLayout', () => {
  it('loads up to one hundred todos so the navigation badge reflects the full count', async () => {
    getTodos.mockResolvedValue({
      items: [],
      total: 7,
      pendingApprovalCount: 7,
      dueSoonCount: 0,
      overdueCount: 0,
      ocrFailedCount: 0,
      generatedAt: '2026-09-20T10:00:00',
    })

    const wrapper = mount(AppLayout, {
      global: {
        stubs: { 'el-button': true, 'el-icon': true, 'router-link': RouterLinkStub, 'router-view': true },
      },
    })

    await flushPromises()

    expect(getTodos).toHaveBeenCalledWith(100)
    expect(wrapper.find('.nav-count').text()).toBe('7')
  })
})
