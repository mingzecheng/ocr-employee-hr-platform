import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import OrganizationView from './OrganizationView.vue'

const { getDepartmentTree, listEmployees } = vi.hoisted(() => ({
  getDepartmentTree: vi.fn(),
  listEmployees: vi.fn(),
}))

vi.mock('@/api/organization', () => ({ getDepartmentTree }))
vi.mock('@/api/archive', () => ({ listEmployees }))

const RouterLinkStub = defineComponent({
  setup(_, { slots }) {
    return () => h('a', slots.default?.())
  },
})

describe('OrganizationView', () => {
  it('loads departments and employees and calculates directory statistics', async () => {
    getDepartmentTree.mockResolvedValue([
      { id: 1, parentId: null, code: 'OPS', name: '运营中心', sortNo: 1, active: true },
      { id: 2, parentId: 1, code: 'HR', name: '人力资源部', sortNo: 1, active: true },
    ])
    listEmployees.mockResolvedValue({
      items: [
        { id: 10, employeeNo: 'E-010', name: '张三', idCardNo: null, departmentId: 2, status: 'ACTIVE' },
        { id: 11, employeeNo: 'E-011', name: '李四', idCardNo: null, departmentId: null, status: 'INACTIVE' },
      ],
      total: 2,
      page: 1,
      pageSize: 100,
    })

    const wrapper = mount(OrganizationView, { global: { stubs: { 'el-button': true, 'el-icon': true, 'router-link': RouterLinkStub } } })
    await flushPromises()

    expect(getDepartmentTree).toHaveBeenCalledOnce()
    expect(listEmployees).toHaveBeenCalledWith(1, 100)
    expect(wrapper.text()).toContain('运营中心')
    expect(wrapper.text()).toContain('人力资源部')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.get('.directory-stats').text()).toContain('1')
    expect(wrapper.get('.directory-stats').text()).toContain('待归属')
  })

  it('keeps an explicit error state when the organization request fails', async () => {
    getDepartmentTree.mockRejectedValue(new Error('network error'))
    listEmployees.mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 })

    const wrapper = mount(OrganizationView, { global: { stubs: { 'el-button': true, 'el-icon': true, 'router-link': RouterLinkStub } } })
    await flushPromises()

    expect(wrapper.text()).toContain('network error')
  })
})
