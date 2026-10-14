import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus, { ElMessage } from 'element-plus'
import { request } from '../api/http'
import RolesView from './RolesView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() } }
})
const requestMock = vi.mocked(request)
const successMock = vi.mocked(ElMessage.success)
const errorMock = vi.mocked(ElMessage.error)

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

function mountView() {
  return mount(RolesView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
}

describe('RolesView', () => {
  beforeEach(() => {
    requestMock.mockReset()
    successMock.mockClear()
    errorMock.mockClear()
    requestMock.mockResolvedValue(matrixPayload())
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('loads /admin/roles and renders role friendly names plus module headers', async () => {
    const wrapper = mountView()
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
    const wrapper = mountView()
    await flushPromises()

    // 2 角色 × 3 模块 = 6 个单元格：4 个 true，2 个 false
    const checks = wrapper.findAll('.data-table .cell-check')
    const crosses = wrapper.findAll('.data-table .cell-cross')
    expect(checks.length).toBe(4)
    expect(crosses.length).toBe(2)
    wrapper.unmount()
  })

  it('toggles a denied cell to allowed locally and marks it dirty', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('.data-table .cell-cross').length).toBe(2)
    expect(wrapper.text()).not.toContain('项未保存')

    // 第一个 ❌ 单元格 = EMPLOYEE / 人事审批
    const firstCross = wrapper.find('.data-table .cell-toggle .cell-cross')
    await firstCross.trigger('click')
    await flushPromises()

    // 本地切换：剩下 1 个叉、5 个勾，且底部提示 1 项未保存
    expect(wrapper.findAll('.data-table .cell-cross').length).toBe(1)
    expect(wrapper.findAll('.data-table .cell-check').length).toBe(5)
    expect(wrapper.text()).toContain('1 项未保存')
    wrapper.unmount()
  })

  it('calls PUT for each dirty cell on save and toasts success', async () => {
    const wrapper = mountView()
    await flushPromises()

    // 切换两个单元格：EMPLOYEE/人事审批 -> true，EMPLOYEE/系统管理 -> true
    await wrapper.findAll('.data-table .cell-toggle .cell-cross')[0].trigger('click')
    await flushPromises()
    await wrapper.findAll('.data-table .cell-toggle .cell-cross')[0].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('2 项未保存')

    const saveBtn = wrapper.findAll('button').find((b) => b.text().includes('保存'))
    expect(saveBtn).toBeTruthy()
    await saveBtn!.trigger('click')
    await flushPromises()

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/admin/roles/EMPLOYEE/permissions',
      method: 'PUT',
      data: { module: '人事审批', allowed: true },
    }))
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/admin/roles/EMPLOYEE/permissions',
      method: 'PUT',
      data: { module: '系统管理', allowed: true },
    }))
    expect(successMock).toHaveBeenCalledWith('权限已更新')
    expect(wrapper.text()).not.toContain('项未保存')
    wrapper.unmount()
  })

  it('toasts error and keeps dirty list when a save request fails', async () => {
    requestMock.mockImplementation(async (config: { url?: string; method?: string }) => {
      if (config.method === 'PUT') throw new Error('没有权限修改角色权限')
      return matrixPayload() as never
    })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('.data-table .cell-toggle .cell-cross').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('1 项未保存')

    const saveBtn = wrapper.findAll('button').find((b) => b.text().includes('保存'))
    await saveBtn!.trigger('click')
    await flushPromises()

    expect(errorMock).toHaveBeenCalledWith(expect.stringContaining('没有权限修改角色权限'))
    // 失败后 dirty 保留，便于重试
    expect(wrapper.text()).toContain('1 项未保存')
    wrapper.unmount()
  })
})
