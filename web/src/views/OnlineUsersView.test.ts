import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api/http'
import OnlineUsersView from './OnlineUsersView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
    ElMessageBox: { confirm: vi.fn() },
  }
})

const requestMock = vi.mocked(request)
const confirmMock = vi.mocked(ElMessageBox.confirm)
const successMock = vi.mocked(ElMessage.success)

const onlineUsers = [
  { userId: 10, username: 'alice', roles: ['HR_ADMIN'], loginApprox: '2026-10-13T09:00:00' },
  { userId: 20, username: 'bob', roles: ['EMPLOYEE'], loginApprox: '2026-10-13T09:30:00' },
]

describe('OnlineUsersView', () => {
  beforeEach(() => {
    requestMock.mockReset()
    confirmMock.mockReset()
    successMock.mockReset()
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/admin/online-users') return onlineUsers as never
      return undefined as never
    })
  })
  afterEach(() => { document.body.innerHTML = '' })

  it('loads and renders the online user list', async () => {
    const wrapper = mount(OnlineUsersView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/admin/online-users' }))
    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('bob')
    wrapper.unmount()
  })

  it('asks for confirmation then calls DELETE and refreshes the list', async () => {
    confirmMock.mockResolvedValue('confirm' as never)
    const wrapper = mount(OnlineUsersView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    // 初始拉取一次
    expect(requestMock).toHaveBeenCalledTimes(1)

    const vm = wrapper.vm as any
    await vm.forceLogout(onlineUsers[1])
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/admin/online-users/20', method: 'DELETE' }))
    expect(successMock).toHaveBeenCalledWith(expect.stringContaining('bob'))
    // 删除成功后刷新列表
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({ url: '/admin/online-users' }))
    wrapper.unmount()
  })

  it('does not call DELETE when the operator cancels the confirmation', async () => {
    confirmMock.mockRejectedValue(new Error('cancel') as never)
    const wrapper = mount(OnlineUsersView, { attachTo: document.body, global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.forceLogout(onlineUsers[0])
    await flushPromises()

    expect(requestMock).not.toHaveBeenCalledWith(expect.objectContaining({ method: 'DELETE' }))
    wrapper.unmount()
  })
})
