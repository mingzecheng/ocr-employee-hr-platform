import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkflowView from './WorkflowView.vue'

const { approveHrRequest, createHrRequest, getHrRequest, rejectHrRequest, submitHrRequest } = vi.hoisted(() => ({
  approveHrRequest: vi.fn(),
  createHrRequest: vi.fn(),
  getHrRequest: vi.fn(),
  rejectHrRequest: vi.fn(),
  submitHrRequest: vi.fn(),
}))

vi.mock('@/api/workflow', () => ({ approveHrRequest, createHrRequest, getHrRequest, rejectHrRequest, submitHrRequest }))

const ButtonStub = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots, emit }) {
    return () => h('button', {
      type: attrs['native-type'] === 'submit' ? 'submit' : 'button',
      onClick: (event: MouseEvent) => emit('click', event),
    }, slots.default?.())
  },
})

const draftRequest = {
  id: 41,
  requestNo: 'HR-202609-0041',
  requestType: 'ONBOARDING',
  employeeId: 1001,
  applicantId: 9,
  payload: { reason: '补充入职材料', handoverNote: '' },
  status: 'DRAFT',
  currentNode: 'DRAFT',
}

function mountView() {
  return mount(WorkflowView, { global: { stubs: { 'el-button': ButtonStub, 'el-icon': true } } })
}

describe('WorkflowView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    createHrRequest.mockResolvedValue(draftRequest)
    submitHrRequest.mockResolvedValue({ ...draftRequest, status: 'PENDING_DEPT_APPROVAL', currentNode: 'DEPT_APPROVAL' })
  })

  it('shows an empty state before a request is created or loaded', () => {
    const wrapper = mountView()

    expect(wrapper.text()).toContain('创建或加载一条申请')
    expect(wrapper.text()).toContain('固定审批链')
  })

  it('creates an HR request with the form payload and can submit it for approval', async () => {
    const wrapper = mountView()
    await wrapper.get('form input[type="number"]').setValue(1001)
    await wrapper.find('textarea').setValue('补充入职材料')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(createHrRequest).toHaveBeenCalledWith({
      requestType: 'ONBOARDING',
      employeeId: 1001,
      payload: { effectiveDate: null, targetDepartmentId: null, reason: '补充入职材料', handoverNote: '' },
    })
    expect(wrapper.text()).toContain('HR-202609-0041')

    await wrapper.findAll('button').find((button) => button.text().includes('提交审批'))!.trigger('click')
    await flushPromises()

    expect(submitHrRequest).toHaveBeenCalledWith(41)
    expect(wrapper.text()).toContain('部门审批中')
  })
})
