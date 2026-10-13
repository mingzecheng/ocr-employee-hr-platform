import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { request } from '../api/http'
import RolesView from './RolesView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
const requestMock = vi.mocked(request)

function matrixPayload() {
  return {
    roles: ['SYSTEM_ADMIN', 'EMPLOYEE'],
    modules: ['员工档案', '人事审批', '系统管理'],
    matrix: {
      SYSTEM_ADMIN: { 员工档案: true, 人事审批: true, 系统管理: true },
      EMPLOYEE: { 员工档案: true, 人事审批: false, 系统管理: false },
    },
  } as never
}

describe('RolesView', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockResolvedValue(matrixPayload())
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('loads /admin/roles and renders role friendly names plus module headers', async () => {
    const wrapper = mount(RolesView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/admin/roles' }))
    const text = wrapper.text()
    expect(text).toContain('系统管理员')
    expect(text).toContain('普通员工')
    expect(text).toContain('员工档案')
    expect(text).toContain('系统管理')
    wrapper.unmount()
  })

  it('renders check icons for permitted cells and cross icons for denied cells', async () => {
    const wrapper = mount(RolesView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    // 2 角色 × 3 模块 = 6 个单元格：4 个 true（全选3 + 员工档案1），2 个 false；
    // 限定在表格内统计，排除底部图例里的图标
    const checks = wrapper.findAll('.data-table .cell-check')
    const crosses = wrapper.findAll('.data-table .cell-cross')
    expect(checks.length).toBe(4)
    expect(crosses.length).toBe(2)
    wrapper.unmount()
  })
})
