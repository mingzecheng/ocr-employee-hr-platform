import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { request } from '../api/http'
import { useAuthStore } from '../stores/auth'
import EmployeeListView from './EmployeeListView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
const requestMock = vi.mocked(request)
const employee = {
  id: 7,
  employeeNo: 'EMP-007',
  name: '原姓名',
  phone: '13800138001',
  departmentId: 3,
  positionId: 4,
  status: 'ACTIVE',
}

function mountView(roles: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore(pinia).user = {
    id: 99, username: 'test-user', employeeId: null, departmentId: null, roles, dataScope: 'ALL',
  }
  return mount(EmployeeListView, {
    attachTo: document.body,
    global: {
      plugins: [pinia, ElementPlus],
      mocks: { $router: { push: vi.fn() } },
    },
  })
}

describe('EmployeeListView', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/employees/7/profile' && config.method === 'PUT') return { ...employee, name: '新姓名' } as never
      if (config.url === '/employees') return { items: [employee], total: 1, page: 1, pageSize: 10 } as never
      return undefined as never
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('shows profile maintenance only to HR administrators', async () => {
    const hrView = mountView(['HR_ADMIN'])
    await flushPromises()
    expect(hrView.text()).toContain('维护资料')
    hrView.unmount()

    const employeeView = mountView(['EMPLOYEE'])
    await flushPromises()
    expect(employeeView.text()).not.toContain('维护资料')
    employeeView.unmount()
  })

  it('submits only the edited name and phone to the profile endpoint', async () => {
    const wrapper = mountView(['HR_ADMIN'])
    await flushPromises()
    const action = wrapper.findAll('button').find((button) => button.text().includes('维护资料'))
    expect(action).toBeDefined()
    await action!.trigger('click')
    await flushPromises()

    const vm = wrapper.vm as any
    vm.editForm.name = '新姓名'
    vm.editForm.phone = ''
    await vm.saveProfile()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/employees/7/profile',
      method: 'PUT',
      data: { name: '新姓名', phone: '' },
    }))
    wrapper.unmount()
  })

  it('renders a blue on-leave badge for employees currently on leave', async () => {
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/employees') {
        return { items: [{ ...employee, status: 'ON_LEAVE' }], total: 1, page: 1, pageSize: 10 } as never
      }
      return undefined as never
    })

    const wrapper = mountView(['HR_ADMIN'])
    await flushPromises()

    expect(wrapper.text()).toContain('请假中')
    wrapper.unmount()
  })
})
