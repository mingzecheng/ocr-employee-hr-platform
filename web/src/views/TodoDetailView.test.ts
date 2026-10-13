import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { request } from '../api/http'
import TodoDetailView from './TodoDetailView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { prompt: vi.fn(), confirm: vi.fn() } }
})
const routeState = vi.hoisted(() => ({ params: { kind: 'archive-return', id: '8' } }))
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({ roles: ['HR_ADMIN'], user: { id: 20, departmentId: 9 } }),
}))

const requestMock = vi.mocked(request)
const promptBox = vi.mocked(ElMessageBox.prompt)

afterEach(() => {
  requestMock.mockReset()
  promptBox.mockReset()
  routeState.params.kind = 'archive-return'
  routeState.params.id = '8'
  document.body.innerHTML = ''
})

const pendingHrRequest = {
  request: {
    id: 33, requestNo: 'HR-33', requestType: 'LEAVE', employeeId: 7,
    approvalDepartmentId: 10, applicantId: 31, status: 'PENDING_HR_APPROVAL',
    currentNode: 'HR', versionNo: 1, createdAt: '2026-10-01T08:00:00',
    leaveType: 'ANNUAL', leaveStartDate: '2026-10-13', leaveEndDate: '2026-10-20', leaveReason: '年休',
  },
  display: {
    employeeNo: 'EMP-007', employeeName: '林晓', employeeDepartmentId: 10,
    employeePositionName: '工程师', applicantName: '周宁', approvalDepartmentName: '产品研发部',
  },
  approvals: [],
}

describe('TodoDetailView', () => {
  it('lets HR reject a pending archive authorization from its todo detail', async () => {
    routeState.params.kind = 'archive-access'
    const application = {
      id: 8, applicationNo: 'ACC-8', applicantId: 20, departmentId: 9,
      purpose: '年度审计查阅', useType: 'READ', startAt: '2026-10-01T09:00:00',
      dueAt: '2026-10-02T18:00:00', status: 'PENDING_APPROVAL', versionNo: 1,
    }
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/archive-access/8/reject') return { ...application, status: 'REJECTED' } as never
      if (config.url === '/archive-access/8') {
        return {
          application, items: [], uses: [], resolutions: [], decisions: [],
        } as never
      }
      return undefined as never
    })

    const wrapper = mount(TodoDetailView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    const reject = wrapper.findAll('button').find((button) => button.text().trim() === '驳回')
    expect(reject).toBeTruthy()
    await reject?.trigger('click')
    await flushPromises()
    await wrapper.find('textarea').setValue('用途超出授权范围')
    const confirm = wrapper.findAll('button').find((button) => button.text().trim() === '确认驳回')
    await confirm?.trigger('click')
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/archive-access/8/reject', method: 'POST', data: { reason: '用途超出授权范围' },
    }))
    wrapper.unmount()
  })

  it('requires the active archive use to be returned before HR can close an abnormal case', async () => {
    requestMock.mockResolvedValue({
      application: {
        id: 8, applicationNo: 'ACC-8', applicantId: 20, departmentId: 9,
        purpose: '年度审计查阅', useType: 'READ', startAt: '2026-10-01T09:00:00',
        dueAt: '2026-10-02T18:00:00', status: 'ABNORMAL', versionNo: 3,
      },
      applicantName: '林晓', departmentName: '产品研发部', items: [], resolutions: [], decisions: [],
      uses: [{
        id: 12, applicationId: 8, operatorId: 20, usedAt: '2026-10-01T09:10:00',
        returnedAt: null, returnStatus: null, operatorName: '林晓',
      }],
    } as never)

    const wrapper = mount(TodoDetailView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('完成逾期归还')
    expect(wrapper.text()).not.toContain('关闭异常')
    wrapper.unmount()
  })

  it('does not offer another return after an abnormal return has closed the active use', async () => {
    requestMock.mockResolvedValue({
      application: {
        id: 8, applicationNo: 'ACC-8', applicantId: 20, departmentId: 9,
        purpose: '年度审计查阅', useType: 'READ', startAt: '2026-10-01T09:00:00',
        dueAt: '2026-10-02T18:00:00', status: 'ABNORMAL', versionNo: 3,
      },
      applicantName: '林晓', departmentName: '产品研发部',
      items: [{
        id: 60, applicationId: 8, versionId: 70, versionNo: 3, employeeId: 7,
        employeeNo: 'EMP-007', employeeName: '林晓', departmentId: 9,
        departmentName: '产品研发部', documentTitle: '劳动合同', status: 'REQUESTED',
      }],
      uses: [{
        id: 12, applicationId: 8, operatorId: 20, usedAt: '2026-10-01T09:10:00',
        returnedAt: '2026-10-02T18:10:00', returnStatus: 'ABNORMAL',
        remark: '已补登记异常归还', returnedByUserId: 20, operatorName: '林晓', returnedByName: '周宁',
      }],
      resolutions: [], decisions: [],
    } as never)

    const wrapper = mount(TodoDetailView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('异常归还已登记，待人事关闭异常')
    expect(wrapper.text()).not.toContain('补登记归还')
    expect(wrapper.findAll('button').some((button) => button.text().includes('登记归还/移交'))).toBe(false)
    expect(wrapper.text()).toContain('关闭异常')
    expect(wrapper.text()).toContain('林晓')
    expect(wrapper.text()).toContain('产品研发部')
    expect(wrapper.text()).toContain('劳动合同')
    expect(wrapper.text()).toContain('V3')
    expect(wrapper.text()).toContain('周宁')
    wrapper.unmount()
  })

  it('shows readable names and organization context in an HR request detail', async () => {
    routeState.params.kind = 'hr-request'
    routeState.params.id = '33'
    requestMock.mockResolvedValue({
      request: {
        id: 33, requestNo: 'HR-33', requestType: 'TRANSFER', employeeId: 7,
        targetDepartmentId: 11, approvalDepartmentId: 10, targetPositionId: 20,
        applicantId: 31, status: 'PENDING_HR_APPROVAL', currentNode: 'HR', versionNo: 2,
        createdAt: '2026-10-01T08:00:00', effectiveDate: '2026-11-01', reason: '组织调整',
      },
      display: {
        employeeNo: 'EMP-007', employeeName: '林晓', employeeDepartmentId: 10,
        employeeDepartmentName: '产品研发部', employeePositionId: 21,
        employeePositionName: '产品工程师', applicantName: '周宁',
        approvalDepartmentName: '产品研发部', targetDepartmentName: '数据平台部',
        targetPositionName: '后端工程师',
      },
      approvals: [{
        id: 4, requestId: 33, nodeCode: 'DEPARTMENT', approverId: 32,
        approverName: '陈安', decision: 'APPROVED', decidedAt: '2026-10-02T09:30:00',
      }],
    } as never)

    const wrapper = mount(TodoDetailView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('EMP-007 · 林晓')
    expect(wrapper.text()).toContain('产品工程师')
    expect(wrapper.text()).toContain('申请人')
    expect(wrapper.text()).toContain('周宁')
    expect(wrapper.text()).toContain('审批部门')
    expect(wrapper.text()).toContain('数据平台部')
    expect(wrapper.text()).toContain('后端工程师')
    expect(wrapper.text()).toContain('陈安')
    expect(wrapper.text()).not.toContain('账号 #32')
    expect(wrapper.text()).not.toContain('部门 #11')
    wrapper.unmount()
  })

  it('calls the approve endpoint after the comment prompt and disables the button while in flight', async () => {
    routeState.params.kind = 'hr-request'
    routeState.params.id = '33'
    promptBox.mockResolvedValue({ value: '同意休假', action: 'confirm' } as never)
    let release: (value: unknown) => void = () => {}
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/hr-requests/33') return pendingHrRequest as never
      if (config.url === '/hr-requests/33/approve') {
        return new Promise((resolve) => { release = resolve })
      }
      return undefined as never
    })

    const wrapper = mount(TodoDetailView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    const approve = wrapper.findAll('button').find((b) => b.text().includes('通过'))
    expect(approve).toBeTruthy()
    await approve!.trigger('click')
    await flushPromises()

    expect(promptBox).toHaveBeenCalled()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/hr-requests/33/approve', method: 'POST', data: { comment: '同意休假' },
    }))
    // while the decision request is in flight the button must be disabled to block double submit
    expect(wrapper.findAll('button').find((b) => b.text().includes('通过'))!.attributes('disabled')).toBeDefined()

    release({ ...pendingHrRequest.request, status: 'APPROVED' })
    await flushPromises()
    wrapper.unmount()
  })

  it('restores the buttons after a duplicate-submit 409 conflict without crashing', async () => {
    routeState.params.kind = 'hr-request'
    routeState.params.id = '33'
    promptBox.mockResolvedValue({ value: '同意', action: 'confirm' } as never)
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/hr-requests/33') return pendingHrRequest as never
      if (config.url === '/hr-requests/33/approve') {
        throw Object.assign(new Error('请勿重复提交'), { status: 409, code: 'STATE_CONFLICT', handled: true })
      }
      return undefined as never
    })

    const wrapper = mount(TodoDetailView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    const approve = wrapper.findAll('button').find((b) => b.text().includes('通过'))!
    await approve.trigger('click')
    await flushPromises()

    // after the handled 409, the button is re-enabled (loading reset) and the page is still alive
    expect(wrapper.findAll('button').find((b) => b.text().includes('通过'))!.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('待人事复核')
    wrapper.unmount()
  })
})
