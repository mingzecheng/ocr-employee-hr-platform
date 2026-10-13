import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { request } from '../api/http'
import AdminUsersView from './AdminUsersView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
const requestMock = vi.mocked(request)

describe('AdminUsersView', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/admin/users') return { items: [], total: 0, page: 1, pageSize: 20 } as never
      if (config.url === '/admin/users/roles') return [{ code: 'SYSTEM_ADMIN', name: '系统管理员' }] as never
      return undefined as never
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('loads role options and shows an empty account state', async () => {
    const wrapper = mount(AdminUsersView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('暂无账号')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/admin/users/roles' }))
    wrapper.unmount()
  })

  it('submits a complete account and role set', async () => {
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/admin/users' && config.method === 'POST') return { id: 3, username: 'sys-3', enabled: true, roleCodes: ['SYSTEM_ADMIN'] } as never
      if (config.url === '/admin/users') return { items: [], total: 0, page: 1, pageSize: 20 } as never
      if (config.url === '/admin/users/roles') return [{ code: 'SYSTEM_ADMIN', name: '系统管理员' }] as never
      return undefined as never
    })
    const wrapper = mount(AdminUsersView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.openForm()
    await flushPromises()
    await vi.waitFor(() => expect(vm.formRef).toBeTruthy())
    vm.form.username = 'sys-3'
    vm.form.password = 'password123'
    vm.form.passwordConfirm = 'password123'
    vm.form.roleCodes = ['SYSTEM_ADMIN']
    await expect(vm.formRef.validate()).resolves.toBe(true)
    await vm.save()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/admin/users', method: 'POST', data: expect.objectContaining({ username: 'sys-3', roleCodes: ['SYSTEM_ADMIN'] }) }))
    wrapper.unmount()
  })

  it('rejects mismatched password confirmation before calling the API', async () => {
    const wrapper = mount(AdminUsersView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.form.username = 'sys-3'
    vm.form.password = 'password123'
    vm.form.passwordConfirm = 'different'
    vm.form.roleCodes = ['SYSTEM_ADMIN']
    await vm.save()
    expect(requestMock).not.toHaveBeenCalledWith(expect.objectContaining({ method: 'POST' }))
    wrapper.unmount()
  })
})
