import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { request } from '../api/http'
import InventoryView from './InventoryView.vue'

vi.mock('../api/http', () => ({ request: vi.fn() }))

const requestMock = vi.mocked(request)
const task = { id: 4, taskNo: 'INV-DEMO-4', status: 'IN_PROGRESS', initiatorId: 1 }
const item = {
  id: 9, taskId: 4, expectedVersionId: 31, expectedVersionNo: 1,
  actualVersionId: null, employeeId: 7, departmentId: 2,
  employeeNo: 'EMP-007', employeeName: '林晓', documentTitle: '劳动合同',
  actualStatus: null, differenceType: null, differenceDescription: null, checkedBy: null,
  resolutionStatus: 'PENDING', resolution: null,
}

beforeEach(() => {
  requestMock.mockReset()
  requestMock.mockImplementation(async (config) => {
    if (config.url === '/inventory-tasks') {
      return { items: [task], total: 1, page: 1, pageSize: 10 } as never
    }
    if (config.url === '/inventory-tasks/4/items') return [item] as never
    if (config.url === '/inventory-tasks/4/items/9/versions') {
      return [
        { id: 31, versionNo: 1, isCurrent: false, status: 'PUBLISHED' },
        { id: 32, versionNo: 2, isCurrent: true, status: 'PUBLISHED' },
      ] as never
    }
    return undefined as never
  })
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('InventoryView', () => {
  it('creates an inventory task from employee, document, and uploaded version choices', async () => {
    const selectedTask = { id: 5, taskNo: 'INV-DEMO-5', status: 'DRAFT', initiatorId: 1 }
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/inventory-tasks' && config.method === 'POST') return selectedTask as never
      if (config.url === '/inventory-tasks') {
        return { items: [task, selectedTask], total: 2, page: 1, pageSize: 10 } as never
      }
      if (config.url === '/inventory-tasks/5/items') return [] as never
      if (config.url === '/employees') {
        return {
          items: [{ id: 7, employeeNo: 'EMP-007', name: '林晓', departmentId: 2, positionId: 3, status: 'ACTIVE' }],
          total: 1, page: 1, pageSize: 20,
        } as never
      }
      if (config.url === '/archive/employees/7/documents') {
        return {
          items: [{ id: 21, employeeId: 7, documentType: 'CONTRACT', title: '劳动合同' }],
          total: 1, page: 1, pageSize: 100,
        } as never
      }
      if (config.url === '/archive/documents/21/versions') {
        return {
          items: [
            { id: 45, documentId: 21, versionNo: 1, originalName: 'contract.png', contentType: 'image/png', size: 20, sha256: 'hash', status: 'UPLOADED', isCurrent: true, createdAt: '2026-10-01' },
            { id: 46, documentId: 21, versionNo: 2, originalName: 'contract-old.png', contentType: 'image/png', size: 20, sha256: 'hash', status: 'UPLOADED', isCurrent: false, createdAt: '2026-10-01' },
          ],
          total: 2, page: 1, pageSize: 100,
        } as never
      }
      return undefined as never
    })

    const wrapper = mount(InventoryView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    const createButton = wrapper.findAll('button').find((button) => button.text().includes('创建盘点任务'))
    await createButton?.trigger('click')
    await flushPromises()

    const employeeControl = document.body.querySelector('.inventory-employee-select .el-select__wrapper')
    employeeControl?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    const employeeSearch = document.body.querySelector('.inventory-employee-select input') as HTMLInputElement
    employeeSearch.value = 'EMP-007'
    employeeSearch.dispatchEvent(new Event('input', { bubbles: true }))
    await vi.waitFor(() => expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/employees', params: expect.objectContaining({ keyword: 'EMP-007' }),
    })))
    await flushPromises()

    const clickOption = async (text: string) => {
      const option = Array.from(document.body.querySelectorAll('.el-select-dropdown__item'))
        .find((item) => item.textContent?.includes(text) && !item.classList.contains('is-disabled'))
      expect(option).toBeTruthy()
      option?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushPromises()
    }
    const openSelect = async (selector: string) => {
      const control = document.body.querySelector(`${selector} .el-select__wrapper`)
      control?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushPromises()
    }

    await clickOption('EMP-007')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/archive/employees/7/documents',
    }))
    await openSelect('.inventory-document-select')
    await clickOption('劳动合同')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/archive/documents/21/versions',
    }))
    await openSelect('.inventory-version-select')
    await clickOption('V1 · 当前版本')

    const addButton = Array.from(document.body.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('加入范围'))
    addButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(document.body.textContent).toContain('EMP-007')
    expect(document.body.textContent).toContain('劳动合同')
    expect(document.body.textContent).not.toContain('45')

    await openSelect('.inventory-version-select')
    const duplicateOption = Array.from(document.body.querySelectorAll('.inventory-create-versions-popper .el-select-dropdown__item'))
      .find((item) => item.textContent?.includes('V1 · 当前版本'))
    expect(duplicateOption?.classList.contains('is-disabled')).toBe(true)

    const submitButton = Array.from(document.body.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === '创建')
    submitButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/inventory-tasks', method: 'POST', data: { versionIds: [45] },
    }))

    wrapper.unmount()
  })

  it('shows an empty state when employee search returns no records in scope', async () => {
    requestMock.mockImplementation(async (config) => {
      if (config.url === '/employees') return { items: [], total: 0, page: 1, pageSize: 20 } as never
      if (config.url === '/inventory-tasks') {
        return { items: [], total: 0, page: 1, pageSize: 10 } as never
      }
      return undefined as never
    })

    const wrapper = mount(InventoryView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    const createButton = wrapper.findAll('button').find((button) => button.text().includes('创建盘点任务'))
    await createButton?.trigger('click')
    await flushPromises()

    document.body.querySelector('.inventory-employee-select .el-select__wrapper')
      ?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    const employeeSearch = document.body.querySelector('.inventory-employee-select input') as HTMLInputElement
    employeeSearch.value = 'OUT-OF-SCOPE'
    employeeSearch.dispatchEvent(new Event('input', { bubbles: true }))
    await vi.waitFor(() => expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/employees', params: expect.objectContaining({ keyword: 'OUT-OF-SCOPE' }),
    })))
    await flushPromises()

    expect(document.body.textContent).toContain('未找到当前权限范围内的员工')
    wrapper.unmount()
  })

  it('opens the item check form from a selected task', async () => {
    const wrapper = mount(InventoryView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('INV-DEMO-4')
    await wrapper.find('button[title="查看盘点明细"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('EMP-007 · 林晓')
    expect(wrapper.text()).toContain('劳动合同')
    expect(wrapper.text()).toContain('V1')
    expect(wrapper.text()).not.toContain('V31')

    await wrapper.find('button[title="登记现场状态"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('登记 V1 盘点实况')
    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/inventory-tasks/4/items/9/versions',
    }))

    await wrapper.find('.inventory-version-select .el-select__wrapper').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('V2 · 当前版本')

    wrapper.unmount()
  })
})
