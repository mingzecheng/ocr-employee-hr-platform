import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EmployeeListView from './EmployeeListView.vue'

const { createEmployee, listEmployees, push } = vi.hoisted(() => ({
  createEmployee: vi.fn(),
  listEmployees: vi.fn(),
  push: vi.fn(),
}))

vi.mock('@/api/archive', () => ({ createEmployee, listEmployees }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }), useRoute: () => ({ query: {} }) }))

const employees = [
  { id: 1, employeeNo: 'E-001', name: '张三', idCardNo: '1101********0011', departmentId: 1, status: 'ACTIVE' },
  { id: 2, employeeNo: 'E-002', name: '李四', idCardNo: null, departmentId: 2, status: 'INACTIVE' },
]

const ButtonStub = defineComponent({
  inheritAttrs: false,
  setup(_, { attrs, slots, emit }) {
    return () => h('button', {
      ...attrs,
      type: attrs['native-type'] === 'submit' ? 'submit' : 'button',
      onClick: (event: MouseEvent) => emit('click', event),
    }, slots.default?.())
  },
})

const InputStub = defineComponent({
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(props, { emit, attrs }) {
    return () => h('input', {
      ...attrs,
      value: props.modelValue,
      onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
    })
  },
})

function mountView() {
  return mount(EmployeeListView, {
    global: {
      stubs: {
        'el-button': ButtonStub,
        'el-icon': true,
        'el-input': InputStub,
        'router-link': { template: '<a><slot /></a>' },
        'el-table': { props: ['data'], template: '<div><div v-for="row in data" :key="row.id">{{ row.name }}</div></div>' },
        'el-table-column': true,
      },
      directives: { loading: () => undefined },
    },
  })
}

describe('EmployeeListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listEmployees.mockResolvedValue({ items: employees, total: employees.length, page: 1, pageSize: 10 })
    createEmployee.mockResolvedValue({ ...employees[0], id: 3, employeeNo: 'E-003', name: '王五' })
  })

  it('loads the first server page and sends status filters back to the API', async () => {
    const wrapper = mountView()

    await flushPromises()
    expect(listEmployees).toHaveBeenCalledWith(1, 10, '', 'ALL')
    expect(wrapper.get('[data-test="employee-result-count"]').text()).toContain('2')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('李四')

    await wrapper.get('[data-test="employee-status-filter"]').setValue('ACTIVE')
    await flushPromises()

    expect(listEmployees).toHaveBeenLastCalledWith(1, 10, '', 'ACTIVE')
  })

  it('requests the next server page instead of slicing a fixed local list', async () => {
    listEmployees.mockResolvedValue({ items: [employees[0]], total: 21, page: 1, pageSize: 10 })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('第 1 / 3 页')
    await wrapper.get('[aria-label="下一页"]').trigger('click')
    await flushPromises()

    expect(listEmployees).toHaveBeenLastCalledWith(2, 10, '', 'ALL')
  })

  it('creates an employee and keeps entered values when creation fails', async () => {
    createEmployee.mockRejectedValueOnce(new Error('员工编号已存在'))
    const wrapper = mountView()
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text().includes('新建员工'))!.trigger('click')

    const form = wrapper.get('[data-test="employee-create-form"]')
    await form.get('[data-test="employee-no"]').setValue('E-003')
    await form.get('[data-test="employee-name"]').setValue('王五')
    await form.get('[data-test="employee-id-card"]').setValue('110101199001011234')
    await form.get('[data-test="employee-department"]').setValue('2')
    await form.trigger('submit')
    await flushPromises()

    expect(createEmployee).toHaveBeenCalledWith({ employeeNo: 'E-003', name: '王五', idCardNo: '110101199001011234', departmentId: 2 })
    expect(wrapper.text()).toContain('员工编号已存在')
    expect((form.get('[data-test="employee-name"]').element as HTMLInputElement).value).toBe('王五')
  })
})
