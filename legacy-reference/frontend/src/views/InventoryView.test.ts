import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import InventoryView from './InventoryView.vue'

const { checkInventoryItem, completeInventory, createInventory, getInventory, resolveInventory, startInventory } = vi.hoisted(() => ({
  checkInventoryItem: vi.fn(),
  completeInventory: vi.fn(),
  createInventory: vi.fn(),
  getInventory: vi.fn(),
  resolveInventory: vi.fn(),
  startInventory: vi.fn(),
}))

vi.mock('@/api/workflow', () => ({ checkInventoryItem, completeInventory, createInventory, getInventory, resolveInventory, startInventory }))

const ButtonStub = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots, emit }) {
    return () => h('button', {
      type: attrs['native-type'] === 'submit' ? 'submit' : 'button',
      onClick: (event: MouseEvent) => emit('click', event),
    }, slots.default?.())
  },
})

const draftTask = {
  id: 63,
  taskNo: 'INV-202609-0063',
  scopeType: 'EMPLOYEE',
  scopeValue: '1001',
  initiatorId: 9,
  status: 'DRAFT',
  startedAt: null,
  completedAt: null,
  items: [{ id: 1, archiveDocumentId: 2001, expectedVersionId: 7, actualVersionId: null, actualStatus: null, differenceType: null, differenceDescription: null, checkedBy: null, checkedAt: null }],
  resolutions: [],
}

function mountView() {
  return mount(InventoryView, { global: { stubs: { 'el-button': ButtonStub, 'el-icon': true } } })
}

describe('InventoryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    createInventory.mockResolvedValue(draftTask)
    startInventory.mockResolvedValue({ ...draftTask, status: 'IN_PROGRESS', startedAt: '2026-09-22T09:00:00' })
  })

  it('shows the empty inventory state initially', () => {
    expect(mountView().text()).toContain('等待一条盘点任务')
  })

  it('creates a task with its inventory item and starts the task', async () => {
    const wrapper = mountView()
    const numberInputs = wrapper.findAll('form input[type="number"]')
    await numberInputs[0].setValue(2001)
    await numberInputs[1].setValue(7)
    await wrapper.get('form input[type="text"]').setValue('1001')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(createInventory).toHaveBeenCalledWith({
      scopeType: 'EMPLOYEE',
      scopeValue: '1001',
      items: [{ archiveDocumentId: 2001, expectedVersionId: 7 }],
    })
    expect(wrapper.text()).toContain('INV-202609-0063')

    await wrapper.findAll('button').find((button) => button.text().includes('开始盘点'))!.trigger('click')
    await flushPromises()

    expect(startInventory).toHaveBeenCalledWith(63)
    expect(wrapper.text()).toContain('盘点中')
  })
})
