import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api/http'
import OrganizationAdminView from './OrganizationAdminView.vue'

const authState = vi.hoisted(() => ({ roles: ['SYSTEM_ADMIN'] as string[] }))
vi.mock('../api/http', () => ({ request: vi.fn() }))
vi.mock('../stores/auth', () => ({ useAuthStore: () => ({ roles: authState.roles }) }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn() },
  }
})
const requestMock = vi.mocked(request)
const confirmMock = vi.mocked(ElMessageBox.confirm)
const messageErrorMock = vi.mocked(ElMessage.error)

describe('OrganizationAdminView', () => {
  beforeEach(() => {
    authState.roles = ['SYSTEM_ADMIN']
    requestMock.mockReset()
    confirmMock.mockReset()
    messageErrorMock.mockReset()
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

  it('clicking delete department button opens a confirmation dialog', async () => {
    confirmMock.mockResolvedValue('confirm' as never)
    const wrapper = mount(OrganizationAdminView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    const vm = wrapper.vm as any
    await vm.confirmDeleteDepartment({ id: 1, code: 'OPS', name: '运营部', parentId: null, managerEmployeeId: null, sortOrder: 1, enabled: true })
    expect(confirmMock).toHaveBeenCalled()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/departments/1', method: 'DELETE' }))
    wrapper.unmount()
  })

  it('surfaces backend 400 conflict message via toast when department delete fails', async () => {
    confirmMock.mockResolvedValue('confirm' as never)
    requestMock.mockRejectedValue(new Error('请先删除该部门下的 3 个岗位') as never)
    const wrapper = mount(OrganizationAdminView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    const vm = wrapper.vm as any
    await vm.confirmDeleteDepartment({ id: 1, code: 'OPS', name: '运营部', parentId: null, managerEmployeeId: null, sortOrder: 1, enabled: true })
    await flushPromises()
    expect(messageErrorMock).toHaveBeenCalledWith(expect.stringContaining('3 个岗位'))
    wrapper.unmount()
  })

  it('clicking delete position button opens a confirmation dialog', async () => {
    confirmMock.mockResolvedValue('confirm' as never)
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/departments/admin') return [{ id: 1, code: 'OPS', name: '运营部', parentId: null, managerEmployeeId: null, sortOrder: 1, enabled: true }] as never
      if (config.url === '/departments/1/positions/admin') return [{ id: 5, departmentId: 1, code: 'DEV', name: '后端工程师', enabled: true }] as never
      return undefined as never
    })
    const wrapper = mount(OrganizationAdminView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    const vm = wrapper.vm as any
    await vm.confirmDeletePosition({ id: 5, departmentId: 1, code: 'DEV', name: '后端工程师', enabled: true })
    expect(confirmMock).toHaveBeenCalled()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/departments/1/positions/5', method: 'DELETE' }))
    wrapper.unmount()
  })

  it('hides the department and position delete buttons for non system administrators', async () => {
    authState.roles = ['HR_ADMIN']
    const wrapper = mount(OrganizationAdminView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.find('button[title="删除部门"]').exists()).toBe(false)
    expect(wrapper.find('button[title="删除岗位"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the department delete button for system administrators', async () => {
    authState.roles = ['SYSTEM_ADMIN']
    const wrapper = mount(OrganizationAdminView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.find('button[title="删除部门"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
