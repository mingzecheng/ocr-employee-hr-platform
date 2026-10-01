import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ArchiveAccessView from './ArchiveAccessView.vue'

const { approveArchiveAccess, checkoutArchiveAccess, createArchiveAccess, getArchiveAccess, rejectArchiveAccess, returnArchiveUse, submitArchiveAccess } = vi.hoisted(() => ({
  approveArchiveAccess: vi.fn(),
  checkoutArchiveAccess: vi.fn(),
  createArchiveAccess: vi.fn(),
  getArchiveAccess: vi.fn(),
  rejectArchiveAccess: vi.fn(),
  returnArchiveUse: vi.fn(),
  submitArchiveAccess: vi.fn(),
}))

vi.mock('@/api/workflow', () => ({ approveArchiveAccess, checkoutArchiveAccess, createArchiveAccess, getArchiveAccess, rejectArchiveAccess, returnArchiveUse, submitArchiveAccess }))

const ButtonStub = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots, emit }) {
    return () => h('button', {
      type: attrs['native-type'] === 'submit' ? 'submit' : 'button',
      onClick: (event: MouseEvent) => emit('click', event),
    }, slots.default?.())
  },
})

const draftApplication = {
  id: 52,
  applicationNo: 'AA-202609-0052',
  applicantId: 9,
  employeeId: 1001,
  useType: 'DIGITAL_ACCESS',
  purpose: '核验员工档案',
  startAt: '2026-09-22T09:00:00',
  dueAt: '2026-09-23T18:00:00',
  returnRequired: true,
  status: 'DRAFT',
  currentNode: 'DRAFT',
  items: [{ id: 1, archiveDocumentId: 2001, archiveVersionId: null, scope: 'READ' }],
  approvals: [],
  use: null,
}

function mountView() {
  return mount(ArchiveAccessView, { global: { stubs: { 'el-button': ButtonStub, 'el-icon': true } } })
}

describe('ArchiveAccessView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    createArchiveAccess.mockResolvedValue(draftApplication)
    submitArchiveAccess.mockResolvedValue({ ...draftApplication, status: 'PENDING_DEPT_APPROVAL', currentNode: 'DEPT_APPROVAL' })
  })

  it('shows the empty authorization state initially', () => {
    expect(mountView().text()).toContain('等待一条授权申请')
  })

  it('creates an archive access application and submits it for approval', async () => {
    const wrapper = mountView()
    const numberInputs = wrapper.findAll('form input[type="number"]')
    await numberInputs[0].setValue(1001)
    await numberInputs[1].setValue(2001)
    await wrapper.find('textarea').setValue('核验员工档案')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(createArchiveAccess).toHaveBeenCalledWith(expect.objectContaining({
      employeeId: 1001,
      useType: 'DIGITAL_ACCESS',
      purpose: '核验员工档案',
      returnRequired: true,
      items: [{ archiveDocumentId: 2001, archiveVersionId: null, scope: 'READ' }],
    }))
    expect(wrapper.text()).toContain('AA-202609-0052')

    await wrapper.findAll('button').find((button) => button.text().includes('提交审批'))!.trigger('click')
    await flushPromises()

    expect(submitArchiveAccess).toHaveBeenCalledWith(52)
    expect(wrapper.text()).toContain('部门审批中')
  })
})
