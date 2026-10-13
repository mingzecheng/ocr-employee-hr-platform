import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { request } from '../api/http'
import ArchiveAccessView from './ArchiveAccessView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
const authState = vi.hoisted(() => ({ roles: ['HR_ADMIN'] as string[], user: { id: 20, departmentId: 9 } }))
const routeState = vi.hoisted(() => ({ query: {} as Record<string, unknown> }))
vi.mock('../stores/auth', () => ({ useAuthStore: () => authState }))
vi.mock('vue-router', () => ({ useRoute: () => routeState }))

const requestMock = vi.mocked(request)
const application = {
  id: 8, applicationNo: 'ACC-8', applicantId: 20, departmentId: 9, purpose: '部门交接',
  useType: 'HANDOVER', startAt: '2026-10-01T09:00:00', dueAt: '2026-10-03T18:00:00',
  status: 'IN_USE', versionNo: 2, createdAt: '2026-10-01T08:00:00',
}

beforeEach(() => {
  authState.roles = ['HR_ADMIN']
  routeState.query = {}
  requestMock.mockReset()
  requestMock.mockImplementation(async (config) => {
    if (config.url === '/archive-access') {
      return { items: [application], total: 1, page: 1, pageSize: 10 } as never
    }
    if (config.url === '/archive-access/8') {
      return {
        application,
        applicantName: '林晓', departmentName: '产品研发部',
        items: [{ id: 1, applicationId: 8, versionId: 31, versionNo: 4, employeeId: 7,
          employeeNo: 'EMP-007', employeeName: '林晓', departmentId: 9, departmentName: '产品研发部',
          documentTitle: '劳动合同', status: 'REQUESTED' }],
        uses: [{ id: 2, applicationId: 8, operatorId: 20, usedAt: '2026-10-01T09:20:00',
          returnedAt: '2026-10-01T10:00:00', returnStatus: 'HANDED_OVER', remark: '交接完成',
          operatorName: '林晓', returnedByUserId: 20, returnedByName: '周宁', handoverToEmployeeId: 72,
          handoverToEmployeeNo: 'EMP-072', handoverToEmployeeName: '李四' }],
        resolutions: [{ id: 5, applicationId: 8, resolvedByUserId: 30, resolvedByName: '王主管',
          resolutionNote: '已补齐缺页并复核', resolvedAt: '2026-10-02T11:00:00' }],
        decisions: [],
      } as never
    }
    if (config.url === '/employees') {
      return { items: [{ id: 72, employeeNo: 'EMP-072', name: '李四', departmentId: 9, status: 'ACTIVE' }],
        total: 1, page: 1, pageSize: 100 } as never
    }
    return undefined as never
  })
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('ArchiveAccessView', () => {
  it('lets HR preview a pending item for approval and limits active reads to the current applicant', async () => {
    const pending = { ...application, status: 'PENDING_APPROVAL' }
    const detailFor = (item: typeof pending) => ({
      application: item, items: [{ id: 1, applicationId: 8, versionId: 31, versionNo: 4,
        employeeId: 7, departmentId: 9, documentTitle: '劳动合同', status: 'REQUESTED' }],
      uses: [], resolutions: [], decisions: [],
    })
    routeState.query = { applicationId: '8' }
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/archive-access') return { items: [pending], total: 1, page: 1, pageSize: 10 } as never
      if (config.url === '/archive-access/8') return detailFor(pending) as never
      return undefined as never
    })

    const hrWrapper = mount(ArchiveAccessView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(hrWrapper.find('button[title="查看已授权原图"]').exists()).toBe(true)
    hrWrapper.unmount()

    const inUse = { ...pending, applicantId: 20, status: 'IN_USE',
      startAt: new Date(Date.now() - 60_000).toISOString(), dueAt: new Date(Date.now() + 60_000).toISOString() }
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/archive-access') return { items: [inUse], total: 1, page: 1, pageSize: 10 } as never
      if (config.url === '/archive-access/8') return detailFor(inUse) as never
      return undefined as never
    })
    authState.roles = ['EMPLOYEE']

    const employeeWrapper = mount(ArchiveAccessView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(employeeWrapper.find('button[title="查看已授权原图"]').exists()).toBe(true)
    employeeWrapper.unmount()

    const otherApplicant = { ...inUse, applicantId: 21 }
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/archive-access') return { items: [otherApplicant], total: 1, page: 1, pageSize: 10 } as never
      if (config.url === '/archive-access/8') return detailFor(otherApplicant) as never
      return undefined as never
    })
    const otherEmployeeWrapper = mount(ArchiveAccessView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    expect(otherEmployeeWrapper.find('button[title="查看已授权原图"]').exists()).toBe(false)
    otherEmployeeWrapper.unmount()
  })

  it('opens a prefilled request for the employee and archive version selected on the archive page', async () => {
    routeState.query = { employeeId: '7', versionId: '31' }
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/archive-access') return { items: [], total: 0, page: 1, pageSize: 10 } as never
      if (config.url === '/employees') return { items: [{ id: 7, employeeNo: 'EMP-007', name: '林晓',
        departmentId: 9, status: 'ACTIVE' }], total: 1, page: 1, pageSize: 100 } as never
      if (config.url === '/archive/employees/7/documents') return { items: [{ id: 21, title: '员工档案' }],
        total: 1, page: 1, pageSize: 100 } as never
      if (config.url === '/archive/documents/21/versions') return { items: [{ id: 31, documentId: 21,
        versionNo: 1, originalName: 'profile.png', size: 10, createdAt: '2026-10-01T09:00:00',
        isCurrent: true }], total: 1, page: 1, pageSize: 100 } as never
      return undefined as never
    })

    const wrapper = mount(ArchiveAccessView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/archive/documents/21/versions' }))
    expect(document.body.textContent).toContain('profile.png · V1')
    wrapper.unmount()
  })

  it('lets the applicant submit a draft access request from the list', async () => {
    const draft = { ...application, id: 11, applicationNo: 'ACC-11', status: 'DRAFT' }
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/archive-access') {
        return { items: [draft], total: 1, page: 1, pageSize: 10 } as never
      }
      if (config.url === '/archive-access/11/submit') {
        return { ...draft, status: 'PENDING_APPROVAL' } as never
      }
      return undefined as never
    })
    authState.roles = ['EMPLOYEE']

    const wrapper = mount(ArchiveAccessView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    const submitButton = wrapper.findAll('button').find((button) => button.attributes('title') === '提交授权申请')
    expect(submitButton).toBeTruthy()
    await submitButton?.trigger('click')
    await flushPromises()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/archive-access/11/submit', method: 'POST',
    }))

    wrapper.unmount()
  })

  it('does not expose approval actions to employees', async () => {
    const pending = { ...application, status: 'PENDING_APPROVAL' }
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/archive-access') {
        return { items: [pending], total: 1, page: 1, pageSize: 10 } as never
      }
      return undefined as never
    })
    authState.roles = ['EMPLOYEE']

    const wrapper = mount(ArchiveAccessView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.findAll('button').some((button) => button.text().trim() === '授权')).toBe(false)
    wrapper.unmount()
  })

  it('lets HR reject a pending application with a required reason', async () => {
    const pending = { ...application, status: 'PENDING_APPROVAL' }
    const rejected = { ...pending, status: 'REJECTED' }
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/archive-access') {
        return { items: [pending], total: 1, page: 1, pageSize: 10 } as never
      }
      if (config.url === '/archive-access/8/reject') return rejected as never
      return undefined as never
    })

    const wrapper = mount(ArchiveAccessView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.find('button[title="驳回授权申请"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('驳回档案授权申请')
    const reason = wrapper.find('textarea')
    await reason.setValue('用途超出授权范围')
    const confirm = wrapper.findAll('button').find((button) => button.text().trim() === '确认驳回')
    await confirm?.trigger('click')
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/archive-access/8/reject',
      method: 'POST',
      data: { reason: '用途超出授权范围' },
    }))
    wrapper.unmount()
  })

  it('shows access and custody records in the application detail', async () => {
    const wrapper = mount(ArchiveAccessView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.find('button[title="查看档案使用记录"]').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('劳动合同')
    expect(document.body.textContent).toContain('V4')
    expect(document.body.textContent).toContain('EMP-007 · 林晓')
    expect(document.body.textContent).toContain('产品研发部')
    expect(document.body.textContent).toContain('EMP-072 · 李四')
    expect(document.body.textContent).toContain('周宁')
    expect(document.body.textContent).toContain('异常处理记录')
    expect(document.body.textContent).toContain('已补齐缺页并复核')
    wrapper.unmount()
  })

  it('loads only in-scope active employees for handover', async () => {
    const wrapper = mount(ArchiveAccessView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.find('button[title="登记归还"]').trigger('click')
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/employees',
      params: expect.objectContaining({ departmentId: 9, status: 'ACTIVE' }),
    }))
    await wrapper.find('.handover-select .el-select__wrapper').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('EMP-072')
    wrapper.unmount()
  })
})
