import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import TodoView from './TodoView.vue'
import type { TodoData } from '@/types/api'

const { getTodos } = vi.hoisted(() => ({
  getTodos: vi.fn(),
}))

const { push } = vi.hoisted(() => ({
  push: vi.fn(),
}))

vi.mock('@/api/todos', () => ({ getTodos }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

const data: TodoData = {
  items: [
    { id: 1, type: 'HR_REQUEST_APPROVAL', title: '入职审批', resourceId: 10, status: 'PENDING_HR_APPROVAL', priority: 'HIGH', dueAt: null, createdAt: '2026-09-20T10:00:00', targetPath: '/app/employees/10' },
    { id: 2, type: 'ARCHIVE_ACCESS_APPROVAL', title: '档案访问', resourceId: 11, status: 'PENDING_DEPT_APPROVAL', priority: 'HIGH', dueAt: null, createdAt: '2026-09-20T09:00:00', targetPath: '/app/employees/11' },
    { id: 3, type: 'ARCHIVE_RETURN_DUE', title: '归还档案', resourceId: 12, status: 'IN_USE', priority: 'MEDIUM', dueAt: '2026-09-22T10:00:00', createdAt: '2026-09-19T09:00:00', targetPath: '/app/employees/12' },
    { id: 4, type: 'OCR_FAILED', title: 'OCR 识别失败', resourceId: 13, status: 'FAILED', priority: 'MEDIUM', dueAt: null, createdAt: '2026-09-18T09:00:00', targetPath: '/app/employees/13' },
  ],
  total: 4,
  pendingApprovalCount: 2,
  dueSoonCount: 1,
  overdueCount: 0,
  ocrFailedCount: 1,
  generatedAt: '2026-09-20T10:00:00',
}

describe('TodoView', () => {
  it('shows loading and empty states without treating an empty list as an error', async () => {
    let resolveRequest: (value: TodoData) => void = () => undefined
    getTodos.mockReturnValue(new Promise<TodoData>((resolve) => { resolveRequest = resolve }))
    const wrapper = mount(TodoView, {
      global: { stubs: { 'el-icon': true, 'el-button': true } },
    })

    expect(wrapper.text()).toContain('正在加载待办中心')

    resolveRequest({ ...data, items: [], total: 0 })
    await flushPromises()

    expect(wrapper.text()).toContain('暂无待处理事项')
  })

  it('renders all todo types and priority markers after loading', async () => {
    getTodos.mockResolvedValue(data)
    const wrapper = mount(TodoView, {
      global: { stubs: { 'el-icon': true, 'el-button': true } },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('HR 审批')
    expect(wrapper.text()).toContain('档案访问审批')
    expect(wrapper.text()).toContain('归还到期')
    expect(wrapper.text()).toContain('OCR 识别失败')
    expect(wrapper.text()).toContain('部门审批中')
    expect(wrapper.text()).not.toContain('PENDING_DEPT_APPROVAL')
    expect(wrapper.findAll('.todo-item.priority-high')).toHaveLength(2)
    expect(wrapper.findAll('.todo-item.priority-medium')).toHaveLength(2)
    await wrapper.get('[data-test="todo-item-1"]').trigger('click')
    expect(push).toHaveBeenCalledWith('/app/employees/10')
  })

  it('renders an explicit error state when the api fails', async () => {
    getTodos.mockRejectedValue(new Error('network error'))
    const wrapper = mount(TodoView, {
      global: { stubs: { 'el-icon': true, 'el-button': true } },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('待办数据暂时无法加载')
  })
})
