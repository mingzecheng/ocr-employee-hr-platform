import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { request } from '../api/http'
import OrganizationAdminView from './OrganizationAdminView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
const requestMock = vi.mocked(request)

describe('OrganizationAdminView', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/departments/admin') return [{ id: 1, code: 'OPS', name: '运营部', parentId: null, managerEmployeeId: null, sortOrder: 1, enabled: true }] as never
      if (config.url === '/departments/1/positions/admin') return [] as never
      if (config.method === 'POST' && config.url === '/departments') return { id: 2, code: 'HR', name: '人事部', enabled: true } as never
      return undefined as never
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('loads departments and their positions for maintenance', async () => {
    const wrapper = mount(OrganizationAdminView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('运营部')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/departments/1/positions/admin' }))
    wrapper.unmount()
  })

  it('submits a new department through the typed request wrapper', async () => {
    const wrapper = mount(OrganizationAdminView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    const vm = wrapper.vm as any
    vm.openDepartment()
    await flushPromises()
    vm.departmentForm.code = 'HR'
    vm.departmentForm.name = '人事部'
    await vm.saveDepartment()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/departments', method: 'POST', data: expect.objectContaining({ code: 'HR', name: '人事部' }) }))
    wrapper.unmount()
  })

  it('shows an empty state when no department is available', async () => {
    requestMock.mockResolvedValue([] as never)
    const wrapper = mount(OrganizationAdminView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('暂无部门')
    wrapper.unmount()
  })
})
