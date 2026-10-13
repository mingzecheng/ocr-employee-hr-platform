import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { request } from '../api/http'
import LoginLogsView from './LoginLogsView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
const requestMock = vi.mocked(request)

function pageItems(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    username: `user-${i + 1}`,
    action: i % 2 === 0 ? 'LOGIN' : 'LOGOUT',
    clientIp: `203.0.113.${i + 1}`,
    userAgent: 'Mozilla/5.0 (Macintosh)',
    result: 'SUCCESS',
    createdAt: `2026-10-13T09:0${i}:00`,
  }))
}

describe('LoginLogsView', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockImplementation(async (config) => {
      const page = Number((config.params as { page?: number })?.page ?? 1)
      if (page === 1) return { items: pageItems(2), total: 25 } as never
      return { items: pageItems(3), total: 25 } as never
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('loads /admin/login-logs with page and size params and renders rows', async () => {
    const wrapper = mount(LoginLogsView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/admin/login-logs',
      params: expect.objectContaining({ page: 1, size: 20 }),
    }))
    const text = wrapper.text()
    expect(text).toContain('user-1')
    expect(text).toContain('user-2')
    expect(text).toContain('LOGIN')
    expect(text).toContain('LOGOUT')
    expect(text).toContain('203.0.113.1')
    expect(text).toContain('共 25 条记录')
    wrapper.unmount()
  })

  it('refetches with the next page when pagination changes', async () => {
    const wrapper = mount(LoginLogsView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(requestMock).toHaveBeenCalledTimes(1)

    const vm = wrapper.vm as any
    vm.page = 2
    await vm.load()
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/admin/login-logs',
      params: expect.objectContaining({ page: 2, size: 20 }),
    }))
    expect(wrapper.text()).toContain('user-3')
    wrapper.unmount()
  })
})
