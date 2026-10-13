import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { request } from '../api/http'
import EmployeeArchiveView from './EmployeeArchiveView.vue'

const routerState = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('../api/http', () => ({
  http: { get: vi.fn() },
  request: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '7' } }),
  useRouter: () => routerState,
}))
vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({ roles: ['EMPLOYEE'] }),
}))

const requestMock = vi.mocked(request)

afterEach(() => {
  requestMock.mockReset()
  routerState.push.mockReset()
  document.body.innerHTML = ''
})

describe('EmployeeArchiveView', () => {
  it('keeps upload and OCR-trigger actions read-only for employees', async () => {
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/employees/7') {
        return { id: 7, employeeNo: 'EMP-007', name: '林晓', departmentId: 2, status: 'ACTIVE' } as never
      }
      if (config.url === '/archive/employees/7/documents') {
        return { items: [{ id: 21, employeeId: 7, documentType: 'employee_profile', title: '员工档案' }],
          total: 1, page: 1, pageSize: 20 } as never
      }
      if (config.url === '/archive/documents/21/versions') {
        return { items: [{ id: 31, documentId: 21, versionNo: 1, originalName: 'profile.png',
          size: 10, createdAt: '2026-10-01T09:00:00', isCurrent: true }], total: 1, page: 1, pageSize: 20 } as never
      }
      if (config.url === '/archive/versions/31/ocr-result') return { versionId: 31 } as never
      return undefined as never
    })

    const wrapper = mount(EmployeeArchiveView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.findAll('button').some((button) => button.text().includes('上传材料'))).toBe(false)
    expect(wrapper.findAll('button').some((button) => button.attributes('title') === '触发 OCR')).toBe(false)
    expect(wrapper.findAll('button').some((button) => button.attributes('title') === '查看 OCR 结果')).toBe(true)
    expect(wrapper.text()).toContain('员工档案')
    wrapper.unmount()
  })

  it('routes employees to a prefilled access request instead of direct download', async () => {
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/employees/7') {
        return { id: 7, employeeNo: 'EMP-007', name: '林晓', departmentId: 2, status: 'ACTIVE' } as never
      }
      if (config.url === '/archive/employees/7/documents') {
        return { items: [{ id: 21, employeeId: 7, documentType: 'employee_profile', title: '员工档案' }],
          total: 1, page: 1, pageSize: 20 } as never
      }
      if (config.url === '/archive/documents/21/versions') {
        return { items: [{ id: 31, documentId: 21, versionNo: 1, originalName: 'profile.png',
          size: 10, createdAt: '2026-10-01T09:00:00', isCurrent: true }], total: 1, page: 1, pageSize: 20 } as never
      }
      return undefined as never
    })

    const wrapper = mount(EmployeeArchiveView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.find('button[title="下载原图"]').exists()).toBe(false)
    await wrapper.find('button[title="申请档案访问"]').trigger('click')
    expect(routerState.push).toHaveBeenCalledWith({
      path: '/archive-access',
      query: { employeeId: '7', versionId: '31' },
    })
    wrapper.unmount()
  })

  it('loads and renders the employee status change history', async () => {
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/employees/7') {
        return { id: 7, employeeNo: 'EMP-007', name: '林晓', departmentId: 20, status: 'ACTIVE' } as never
      }
      if (config.url === '/archive/employees/7/documents') {
        return { items: [], total: 0, page: 1, pageSize: 20 } as never
      }
      if (config.url === '/employees/7/status-history') {
        return { items: [{ id: 601, employeeId: 7, beforeStatus: 'PENDING_ONBOARD', afterStatus: 'ACTIVE',
          beforeDepartmentName: '原部门', afterDepartmentName: '新部门', beforePositionName: '原岗位',
          afterPositionName: '新岗位', requestNo: 'HR-501', operatorName: 'hr-admin',
          createdAt: '2026-10-10T09:30:00' }], total: 1, page: 1, pageSize: 20 } as never
      }
      return undefined as never
    })

    const wrapper = mount(EmployeeArchiveView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/employees/7/status-history' }))
    expect(wrapper.text()).toContain('状态与组织变更')
    expect(wrapper.text()).toContain('原部门')
    expect(wrapper.text()).toContain('新部门')
    expect(wrapper.text()).toContain('HR-501')
    wrapper.unmount()
  })

  it('shows a blue on-leave badge with date range when the employee is on leave', async () => {
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/employees/7') {
        return { id: 7, employeeNo: 'EMP-007', name: '林晓', departmentId: 20, status: 'ON_LEAVE',
          leaveStartDate: '2026-10-13', leaveEndDate: '2026-10-20' } as never
      }
      if (config.url === '/archive/employees/7/documents') {
        return { items: [], total: 0, page: 1, pageSize: 20 } as never
      }
      if (config.url === '/employees/7/status-history') {
        return { items: [], total: 0, page: 1, pageSize: 20 } as never
      }
      return undefined as never
    })

    const wrapper = mount(EmployeeArchiveView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('请假中')
    expect(wrapper.text()).toContain('2026-10-13')
    expect(wrapper.text()).toContain('2026-10-20')
    wrapper.unmount()
  })
})
